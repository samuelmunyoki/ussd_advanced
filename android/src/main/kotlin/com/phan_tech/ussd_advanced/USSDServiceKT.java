package com.phan_tech.ussd_advanced;

import android.accessibilityservice.AccessibilityService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.util.Log;
import androidx.annotation.RequiresApi;
import java.util.ArrayList;
import java.util.List;

public class USSDServiceKT extends AccessibilityService {

    private static AccessibilityEvent event;
    private static final String TAG = "USSDServiceKT";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        USSDServiceKT.event = event;
        USSDController ussd = USSDController.INSTANCE;
        Log.d(TAG, String.format(
            "Event Received: [type] %s [class] %s [package] %s [time] %s [text] %s",
            event.getEventType(), event.getClassName(), event.getPackageName(),
            event.getEventTime(), event.getText()));
        
        if (!ussd.isRunning()) {
            return;
        } else {
            Log.d(TAG, "USSDController.INSTANCE running...");
        }
        
        String response = null;

        // If the event has some text.
        if(!event.getText().isEmpty()) {
            List<CharSequence> res = event.getText();

            // Create a copy to avoid ConcurrentModificationException
            List<CharSequence> filteredRes = new ArrayList<>(res);
            // Remove SEND and CANCEL texts from the event
            filteredRes.remove("SEND");
            filteredRes.remove("CANCEL");
            filteredRes.remove("Send");
            filteredRes.remove("Cancel");

            // Join the texts with newlines
            StringBuilder sb = new StringBuilder();
            for (CharSequence s : filteredRes) {
                sb.append(s).append("\n");
            }
            response = sb.toString().trim();
            
            Log.d(TAG, "Processed response: " + response);
        }

        // Print all nodes for debugging
        Log.d(TAG, "Hierarchy dump:");
        dumpNodeHierarchy(event.getSource(), 0);

        // Enhanced detection algorithm
        if (isUSSDWidget(event)) {
            Log.d(TAG, "----- USSD Widget detected. -----");
            
            // Check for input field more thoroughly
            boolean hasInputField = !enhancedNotInputText(event);
            Log.d(TAG, "Has input field: " + hasInputField);
            
            if (!hasInputField) {
                // no input fields - this is likely a final message or menu-only screen
                Log.d(TAG, "No input field detected. Processing as menu or final message.");
                if (LoginView(event)) {
                    // Login view or initial view
                    Log.d(TAG, "Login view detected. Clicking on first button.");
                    enhancedClickOnButton(event, 0);
                    ussd.stopRunning();
                    USSDController.callbackInvoke.over(response != null ? response : "");
                } else if (problemView(event)) {
                    // Error view
                    Log.d(TAG, "Problem view detected. Clicking on second button.");
                    enhancedClickOnButton(event, 1);
                    USSDController.callbackInvoke.over(response != null ? response : "");
                } else {
                    // Regular final message, just click OK/SEND
                    Log.d(TAG, "Final message screen. Clicking OK button.");
                    enhancedClickOnButton(event, 0);
                    ussd.stopRunning();
                    USSDController.callbackInvoke.over(response != null ? response : "");
                }
            } else {
                // Has input field - this is an interactive dialog
                Log.d(TAG, "Input field detected. Processing user input.");
                if (ussd.getSendType() == true)
                    ussd.getCallbackMessage().invoke(event);
                else 
                    USSDController.callbackInvoke.responseInvoke(event);
            }
        } else {
            Log.d(TAG, "Not a USSD widget: " + event.getClassName());
        }
    }

    /**
     * Enhanced method to detect input fields
     */
    protected static boolean enhancedNotInputText(AccessibilityEvent event) {
        if (event == null || event.getSource() == null) return true;
        
        // Create a list to store all edittext nodes
        List<AccessibilityNodeInfo> editTextNodes = new ArrayList<>();
        
        // Search more thoroughly
        findAllEditableNodes(event.getSource(), editTextNodes);
        
        Log.d(TAG, "Found " + editTextNodes.size() + " editable nodes in enhanced search");
        
        return editTextNodes.isEmpty();
    }
    
    /**
     * Recursively find all editable nodes
     */
    private static void findAllEditableNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> editableNodes) {
        if (node == null) return;
        
        // Check if this node is editable
        if (isNodeEditable(node)) {
            editableNodes.add(node);
            Log.d(TAG, "Found editable node: " + node.getClassName() + ", text: " + node.getText());
        }
        
        // Check children recursively
        for (int i = 0; i < node.getChildCount(); i++) {
            findAllEditableNodes(node.getChild(i), editableNodes);
        }
    }
    
    /**
     * Checks if a node is editable using multiple methods
     */
    private static boolean isNodeEditable(AccessibilityNodeInfo node) {
        if (node == null) return false;
        
        String className = node.getClassName().toString();
        
        // Check by class name
        boolean isEditText = className.equals("android.widget.EditText");
        
        // Check by editable flag (API 18+)
        boolean isEditable = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            isEditable = node.isEditable();
        }
        
        // Check if it's focusable and can take text input
        boolean canTakeInput = node.isFocusable() && 
                               (node.isEnabled() && 
                                (node.isClickable() || node.isFocusable()));
        
        return isEditText || isEditable || canTakeInput;
    }

    /**
     * Enhanced button clicking with better fallback options
     */
    protected static void enhancedClickOnButton(AccessibilityEvent event, int index) {
        Log.d(TAG, "Enhanced click on button at index: " + index);
        
        if (event == null || event.getSource() == null) {
            Log.e(TAG, "Event or source is null");
            return;
        }
        
        // Get all clickable nodes
        List<AccessibilityNodeInfo> clickableNodes = new ArrayList<>();
        findAllClickableNodes(event.getSource(), clickableNodes);
        
        Log.d(TAG, "Found " + clickableNodes.size() + " clickable nodes");
        
        // Try to click by index if possible
        if (!clickableNodes.isEmpty() && index < clickableNodes.size()) {
            boolean success = clickableNodes.get(index).performAction(AccessibilityNodeInfo.ACTION_CLICK);
            Log.d(TAG, "Clicked on button at index " + index + ": " + (success ? "succeeded" : "failed"));
            if (success) return;
        }
        
        // Try to find buttons by text
        String[] buttonTexts = {"ok", "send", "cancel", "next", "continue", "confirm"};
        for (AccessibilityNodeInfo node : clickableNodes) {
            if (node.getText() != null) {
                String nodeText = node.getText().toString().toLowerCase();
                // Try to find a button with a known label
                for (String buttonText : buttonTexts) {
                    if (nodeText.contains(buttonText)) {
                        boolean success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        Log.d(TAG, "Clicked on button with text '" + nodeText + "': " + (success ? "succeeded" : "failed"));
                        if (success) return;
                    }
                }
            }
        }
        
        // If still no success, try clicking any button-like node
        for (AccessibilityNodeInfo node : clickableNodes) {
            String className = node.getClassName().toString();
            if (className.contains("Button") || className.contains("button")) {
                boolean success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Log.d(TAG, "Clicked on button-like node: " + (success ? "succeeded" : "failed"));
                if (success) return;
            }
        }
        
        // Last resort: try to click any clickable node
        if (!clickableNodes.isEmpty()) {
            // If index is out of bounds, click the first available button
            int safeIndex = Math.min(index, clickableNodes.size() - 1);
            boolean success = clickableNodes.get(safeIndex).performAction(AccessibilityNodeInfo.ACTION_CLICK);
            Log.d(TAG, "Last resort: clicked on clickable node at index " + safeIndex + ": " + (success ? "succeeded" : "failed"));
        } else {
            Log.e(TAG, "No clickable nodes found at all");
        }
    }
    
    /**
     * Recursively find all clickable nodes
     */
    private static void findAllClickableNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> clickableNodes) {
        if (node == null) return;
        
        // Check if this node is clickable
        if (node.isClickable()) {
            clickableNodes.add(node);
            Log.d(TAG, "Found clickable: " + node.getClassName() + ", text: " + node.getText());
        }
        
        // Check children recursively
        for (int i = 0; i < node.getChildCount(); i++) {
            findAllClickableNodes(node.getChild(i), clickableNodes);
        }
    }

    /**
     * Improved text input method
     */
    public static void send(String text) {
        Log.d(TAG, "Enhanced send: " + text);
        enhancedSetTextIntoField(event, text);
        enhancedClickSendButton(event);
    }
    
    /**
     * Enhanced method to set text in input fields
     */
    private static void enhancedSetTextIntoField(AccessibilityEvent event, String text) {
        if (event == null || event.getSource() == null || text == null) {
            Log.e(TAG, "Cannot set text: event, source or text is null");
            return;
        }
        
        // Find all editable nodes
        List<AccessibilityNodeInfo> editableNodes = new ArrayList<>();
        findAllEditableNodes(event.getSource(), editableNodes);
        
        if (editableNodes.isEmpty()) {
            Log.e(TAG, "No editable nodes found to set text");
            return;
        }
        
        // Try multiple text setting strategies
        boolean textSet = false;
        
        for (AccessibilityNodeInfo node : editableNodes) {
            // Try direct SET_TEXT action (API 21+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Bundle arguments = new Bundle();
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                boolean success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                if (success) {
                    Log.d(TAG, "Successfully set text directly");
                    textSet = true;
                    break;
                }
            }
            
            // Try focus + clipboard paste
            if (node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
                Log.d(TAG, "Node focused, trying clipboard paste");
                
                // Clear existing text if any
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    if (node.getText() != null && !node.getText().toString().isEmpty()) {
                        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION);
                        node.performAction(AccessibilityNodeInfo.ACTION_CUT);
                    }
                }
                
                // Set clipboard and paste
                try {
                    ClipboardManager clipboard = (ClipboardManager) USSDController.INSTANCE
                            .getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("ussd_text", text));
                        boolean pasteSuccess = node.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                        if (pasteSuccess) {
                            Log.d(TAG, "Successfully pasted text from clipboard");
                            textSet = true;
                            break;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error during clipboard paste: " + e.getMessage());
                }
            }
        }
        
        if (!textSet) {
            Log.e(TAG, "Failed to set text using all available methods");
        }
    }
    
    /**
     * Enhanced method to click the send button
     */
    private static void enhancedClickSendButton(AccessibilityEvent event) {
        if (event == null || event.getSource() == null) {
            Log.e(TAG, "Cannot click send: event or source is null");
            return;
        }
        
        // Find all clickable nodes
        List<AccessibilityNodeInfo> clickableNodes = new ArrayList<>();
        findAllClickableNodes(event.getSource(), clickableNodes);
        
        // First, try to find buttons with specific text
        String[] sendButtonTexts = {"send", "next", "continue", "submit", "ok", "confirm"};
        for (AccessibilityNodeInfo node : clickableNodes) {
            if (node.getText() != null) {
                String nodeText = node.getText().toString().toLowerCase();
                for (String buttonText : sendButtonTexts) {
                    if (nodeText.contains(buttonText)) {
                        boolean success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        Log.d(TAG, "Clicked send button with text '" + nodeText + "': " + (success ? "succeeded" : "failed"));
                        if (success) return;
                    }
                }
            }
        }
        
        // If no text-based button found, try by position (usually the right-most or bottom-most button)
        if (!clickableNodes.isEmpty()) {
            // Try the second button if available (often "OK" or "Send")
            if (clickableNodes.size() > 1) {
                boolean success = clickableNodes.get(1).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Log.d(TAG, "Clicked second button: " + (success ? "succeeded" : "failed"));
                if (success) return;
            }
            
            // If that fails, try the first button
            boolean success = clickableNodes.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
            Log.d(TAG, "Clicked first button as fallback: " + (success ? "succeeded" : "failed"));
        } else {
            Log.e(TAG, "No clickable buttons found for send action");
        }
    }

    /**
     * The AccessibilityEvent is instance of USSD Widget class - improved detection
     */
    private boolean isUSSDWidget(AccessibilityEvent event) {
        if (event == null) return false;
        
        String className = event.getClassName().toString();
        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
        
        // Check by class name
        boolean isKnownDialog = (
                className.equals("amigo.app.AmigoAlertDialog")
                || className.equals("com.android.phone.MMIDialogActivity")
                || className.equals("com.android.phone.oppo.settings.LocalAlertDialog")
                || className.equals("android.app.AlertDialog")
                || className.equals("com.android.phone.DialerDialog")
                || className.contains("AlertDialog")
                || className.contains("Dialog")
        );
        
        // Check by package
        boolean isPhonePackage = (
                packageName.contains("phone")
                || packageName.contains("dialer")
                || packageName.contains("mmiservice")
        );
        
        // Enhanced detection: also check the content
        boolean hasUSSDContent = false;
        if (!event.getText().isEmpty()) {
            for (CharSequence text : event.getText()) {
                if (text != null) {
                    String textStr = text.toString().toLowerCase();
                    // Common USSD dialog content patterns
                    if (textStr.contains("ussd") 
                            || textStr.contains("code") 
                            || textStr.contains("service")
                            || textStr.contains("dial")
                            || textStr.contains("running")) {
                        hasUSSDContent = true;
                        break;
                    }
                }
            }
        }
        
        // Log what we found for debugging
        Log.d(TAG, "isUSSDWidget check: isKnownDialog=" + isKnownDialog 
                + ", isPhonePackage=" + isPhonePackage 
                + ", hasUSSDContent=" + hasUSSDContent);
        
        return isKnownDialog || (isPhonePackage && hasUSSDContent);
    }

    // Keep existing helper methods but update to use enhanced versions
    
    private void dumpNodeHierarchy(AccessibilityNodeInfo node, int depth) {
        if (node == null) return;
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
        
        sb.append(node.getClassName())
          .append(", clickable: ").append(node.isClickable())
          .append(", text: ").append(node.getText())
          .append(", id: ").append(node.getViewIdResourceName());
        
        Log.d(TAG, sb.toString());
        
        for (int i = 0; i < node.getChildCount(); i++) {
            dumpNodeHierarchy(node.getChild(i), depth + 1);
        }
    }
    
    private boolean LoginView(AccessibilityEvent event) {
        return isUSSDWidget(event)
                && !event.getText().isEmpty()
                && USSDController.INSTANCE.getMap().get(USSDController.KEY_LOGIN)
                   .contains(event.getText().get(0).toString());
    }

    protected boolean problemView(AccessibilityEvent event) {
        return isUSSDWidget(event)
                && !event.getText().isEmpty()
                && USSDController.INSTANCE.getMap().get(USSDController.KEY_ERROR)
                   .contains(event.getText().get(0).toString());
    }

    protected static boolean notInputText(AccessibilityEvent event) {
        // Delegate to the enhanced version
        return enhancedNotInputText(event);
    }
    
    protected static void clickOnButton(AccessibilityEvent event, int index) {
        // Delegate to the enhanced version
        enhancedClickOnButton(event, index);
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "onInterrupt");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "onServiceConnected");
    }
}