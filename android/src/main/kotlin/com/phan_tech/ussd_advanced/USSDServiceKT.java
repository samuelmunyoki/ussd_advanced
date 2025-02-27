/*
 * Copyright (c) 2020. BoostTag E.I.R.L. Romell D.Z.
 * All rights reserved
 * porfile.romellfudi.com
 */
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


/**
 * AccessibilityService object for ussd dialogs on Android mobile Telcoms
 *
 * @author Romell Dominguez
 * @version 1.1.c 27/09/2018
 * @since 1.0.a
 */
public class USSDServiceKT extends AccessibilityService {

    private static AccessibilityEvent event;
    private static final String TAG = "USSDServiceKT";

    /**
     * Catch widget by Accessibility, when is showing at mobile display
     *
     * @param event AccessibilityEvent
     */
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

        if (LoginView(event) && notInputText(event)) {
            // first view or logView, do nothing, pass / FIRST MESSAGE
            Log.d(TAG, "Login view detected. Clicking on first button.");
            clickOnButton(event, 0);
            ussd.stopRunning();
            USSDController.callbackInvoke.over(response != null ? response : "");
        } else if (problemView(event) || LoginView(event)) {
            // deal down
            Log.d(TAG, "Problem view detected. Clicking on second button.");
            clickOnButton(event, 1);
            USSDController.callbackInvoke.over(response != null ? response : "");
        } else if (isUSSDWidget(event)) {
            Log.d(TAG, "USSD Widget detected.");
            if (notInputText(event)) {
                // not more input panels / LAST MESSAGE
                // sent 'OK' button
                Log.d(TAG, "No input field detected. Closing USSD.");
                clickOnButton(event, 0);
                ussd.stopRunning();
                USSDController.callbackInvoke.over(response != null ? response : "");
            } else {
                // sent option 1
                Log.d(TAG, "Input field detected. Processing user input.");
                if (ussd.getSendType() == true)
                    ussd.getCallbackMessage().invoke(event);
                else USSDController.callbackInvoke.responseInvoke(event);
            }
        }
    }

    /**
     * Dumps the node hierarchy for debugging purposes
     */
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

    /**
     * Send whatever you want via USSD
     *
     * @param text any string
     */
    public static void send(String text) {
        Log.d(TAG, "Sending USSD response: " + text);
        setTextIntoField(event, text);
        // Look for the "SEND" button (try both button position and text)
        clickOnSendButton(event);
    }
    
    public static void send2(String text, AccessibilityEvent ev) {
        Log.d(TAG, "Sending USSD response: " + text);
        setTextIntoField(ev, text);
        clickOnSendButton(ev);
    }

    /**
     * Finds and clicks on a button with "SEND" text or a button at position 1
     */
    private static void clickOnSendButton(AccessibilityEvent event) {
        boolean buttonClicked = false;
        AccessibilityNodeInfo sendButton = null;
        
        for (AccessibilityNodeInfo leaf : getLeaves(event)) {
            CharSequence text = leaf.getText();
            if (leaf.isClickable() && text != null && 
                (text.toString().equalsIgnoreCase("send") || 
                 text.toString().equalsIgnoreCase("next") ||
                 text.toString().equalsIgnoreCase("ok"))) {
                sendButton = leaf;
                break;
            }
        }
        
        // If we found a "SEND" button, click it
        if (sendButton != null) {
            boolean success = sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            Log.d(TAG, "Clicked on SEND button with text: " + sendButton.getText() + 
                  ", success: " + success);
            buttonClicked = success;
        }
        
        // If we didn't find or couldn't click a "SEND" button, try clicking the second button
        if (!buttonClicked) {
            Log.d(TAG, "No SEND button found, trying to click second button");
            clickOnButton(event, 1);
        }
    }

    /**
     * Dismiss dialog by using first button from USSD Dialog
     */
    public static void cancel() {
        Log.d(TAG, "Clicking cancel... ");
        clickOnButton(event, 0);
    }
    
    public static void cancel2(AccessibilityEvent ev) {
        Log.d(TAG, "Clicking cancel... ");
        clickOnButton(ev, 0);
    }

    /**
     * set text into input text at USSD widget
     *
     * @param event AccessibilityEvent
     * @param data  Any String
     */
    private static void setTextIntoField(AccessibilityEvent event, String data) {
        Log.d(TAG, "Attempting to set text: '" + data + "'");
        
        // Get all edit text fields
        List<AccessibilityNodeInfo> editTextNodes = new ArrayList<>();
        for (AccessibilityNodeInfo leaf : getLeaves(event)) {
            if (leaf.getClassName().toString().equals("android.widget.EditText")) {
                editTextNodes.add(leaf);
                Log.d(TAG, "Found EditText: " + leaf.getText());
            }
        }
        
        // If no edit text fields are explicitly found, look for input fields by role
        if (editTextNodes.isEmpty()) {
            for (AccessibilityNodeInfo leaf : getLeaves(event)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    if (leaf.isEditable()) {
                        editTextNodes.add(leaf);
                        Log.d(TAG, "Found editable field: " + leaf.getText());
                    }
                }
            }
        }
        
        // If still no fields found, try to find the parent dialog and look for input fields
        if (editTextNodes.isEmpty() && event.getSource() != null) {
            findEditableNodes(event.getSource(), editTextNodes);
            Log.d(TAG, "Found " + editTextNodes.size() + " editable nodes by recursive search");
        }
        
        boolean textSet = false;
        
        // Try to set text in all found edit text fields
        for (AccessibilityNodeInfo editText : editTextNodes) {
            // Try direct text setting first
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, data);
            
            boolean directSetSucceeded = editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            Log.d(TAG, "Direct text setting " + (directSetSucceeded ? "succeeded" : "failed"));
            
            if (directSetSucceeded) {
                textSet = true;
                break;
            }
            
            // If direct setting failed, try focus then paste
            if (editText.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
                Log.d(TAG, "Successfully focused on edit text");
                
                try {
                    ClipboardManager clipboardManager = ((ClipboardManager) USSDController
                            .INSTANCE.getContext().getSystemService(Context.CLIPBOARD_SERVICE));
                    if (clipboardManager != null) {
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("text", data));
                        boolean pasteSucceeded = editText.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                        Log.d(TAG, "Clipboard paste " + (pasteSucceeded ? "succeeded" : "failed"));
                        
                        if (pasteSucceeded) {
                            textSet = true;
                            break;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error setting text via clipboard: " + e.getMessage());
                }
            }
        }
        
        if (!textSet) {
            Log.e(TAG, "Failed to set text in USSD dialog after trying all methods");
        }
    }
    
    /**
     * Recursively find editable nodes
     */
    private static void findEditableNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> editableNodes) {
        if (node == null) return;
        
        if (node.getClassName().toString().equals("android.widget.EditText") ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && node.isEditable())) {
            editableNodes.add(node);
        }
        
        for (int i = 0; i < node.getChildCount(); i++) {
            findEditableNodes(node.getChild(i), editableNodes);
        }
    }

    /**
     * Method evaluate if USSD widget has input text
     *
     * @param event AccessibilityEvent
     * @return boolean has or not input text
     */
    protected static boolean notInputText(AccessibilityEvent event) {
        // First check for EditText nodes
        for (AccessibilityNodeInfo leaf : getLeaves(event)) {
            if (leaf.getClassName().toString().equals("android.widget.EditText")) {
                return false;
            }
        }
        
        // Then check for editable fields
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            for (AccessibilityNodeInfo leaf : getLeaves(event)) {
                if (leaf.isEditable()) {
                    return false;
                }
            }
        }
        
        // Finally do a recursive check
        if (event.getSource() != null) {
            List<AccessibilityNodeInfo> editableNodes = new ArrayList<>();
            findEditableNodes(event.getSource(), editableNodes);
            return editableNodes.isEmpty();
        }
        
        return true;
    }

    /**
     * The AccessibilityEvent is instance of USSD Widget class
     *
     * @param event AccessibilityEvent
     * @return boolean AccessibilityEvent is USSD
     */
    private boolean isUSSDWidget(AccessibilityEvent event) {
        String className = event.getClassName().toString();
        return (className.equals("amigo.app.AmigoAlertDialog")           // Generic Amigo (possibly Gionee)
                || className.equals("com.android.phone.MMIDialogActivity")
                || className.equals("com.android.phone.oppo.settings.LocalAlertDialog")
                || className.equals("android.app.AlertDialog")          // Standard Android dialog
                || className.equals("com.android.phone.DialerDialog")   // AOSP telephony dialog
                || className.equals("com.oppo.dialer.AlertDialog")      // Oppo (ColorOS) dialer dialog
                || className.equals("com.samsung.android.dialer.DialerDialog") // Samsung (One UI)
                || className.equals("com.miui.dialer.AlertDialog")      // Xiaomi (MIUI)
                || className.equals("com.vivo.dialer.AlertDialog")       // Vivo (Funtouch OS)
                || className.equals("com.huawei.dialer.AlertDialog")    // Huawei (EMUI/HarmonyOS)
                || className.equals("com.google.android.dialer.DialerDialog") // Google (Pixel)
                || className.equals("com.oneplus.dialer.AlertDialog")   // OnePlus (OxygenOS)
                || className.equals("com.realme.dialer.AlertDialog")    // Realme (Realme UI)
                || className.equals("com.motorola.dialer.AlertDialog")  // Motorola
                || className.equals("com.zte.mifavor.widget.AlertDialog") // ZTE (MiFavor)
                || className.equals("color.support.v7.app.AlertDialog") // ColorOS support library
        );
    }

    /**
     * The View has a login message into USSD Widget
     *
     * @param event AccessibilityEvent
     * @return boolean USSD Widget has login message
     */
    private boolean LoginView(AccessibilityEvent event) {
        return isUSSDWidget(event)
                && !event.getText().isEmpty()
                && USSDController.INSTANCE.getMap().get(USSDController.KEY_LOGIN)
                   .contains(event.getText().get(0).toString());
    }

    /**
     * The View has a problem message into USSD Widget
     *
     * @param event AccessibilityEvent
     * @return boolean USSD Widget has problem message
     */
    protected boolean problemView(AccessibilityEvent event) {
        return isUSSDWidget(event)
                && !event.getText().isEmpty()
                && USSDController.INSTANCE.getMap().get(USSDController.KEY_ERROR)
                   .contains(event.getText().get(0).toString());
    }

    /**
     * click a button using the index
     *
     * @param event AccessibilityEvent
     * @param index button's index
     */
    protected static void clickOnButton(AccessibilityEvent event, int index) {
        Log.d(TAG, "Attempting to click button at index: " + index);
        
        List<AccessibilityNodeInfo> clickableNodes = new ArrayList<>();
        
        // Find all clickable nodes
        for (AccessibilityNodeInfo leaf : getLeaves(event)) {
            if (leaf.isClickable()) {
                clickableNodes.add(leaf);
                Log.d(TAG, "Found clickable node: " + leaf.getClassName() + ", text: " + leaf.getText());
            }
        }
        
        // If we found clickable nodes and the index is valid
        if (!clickableNodes.isEmpty() && index < clickableNodes.size()) {
            boolean success = clickableNodes.get(index).performAction(AccessibilityNodeInfo.ACTION_CLICK);
            Log.d(TAG, "Clicked on button at index " + index + ": " + (success ? "succeeded" : "failed"));
        } else {
            Log.e(TAG, "No clickable buttons found or index out of range. Total clickables: " + clickableNodes.size());
            
            // Try finding button by class name as fallback
            for (AccessibilityNodeInfo leaf : getLeaves(event)) {
                if (leaf.getClassName().toString().toLowerCase().contains("button")) {
                    boolean success = leaf.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    Log.d(TAG, "Fallback: clicked on button by class name: " + (success ? "succeeded" : "failed"));
                    if (success) return;
                }
            }
        }
    }

    private static List<AccessibilityNodeInfo> getLeaves(AccessibilityEvent event) {
        List<AccessibilityNodeInfo> leaves = new ArrayList<>();
        if (event != null && event.getSource() != null) {
            getLeaves(leaves, event.getSource());
        }
        return leaves;
    }

    private static void getLeaves(List<AccessibilityNodeInfo> leaves, AccessibilityNodeInfo node) {
        if (node == null) return;
        
        if (node.getChildCount() == 0) {
            leaves.add(node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                getLeaves(leaves, child);
            }
        }
    }

    /**
     * Active when SO interrupt the application
     */
    @Override
    public void onInterrupt() {
        Log.d(TAG, "onInterrupt");
    }

    /**
     * Configure accessibility server from Android Operative System
     */
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "onServiceConnected");
    }
}