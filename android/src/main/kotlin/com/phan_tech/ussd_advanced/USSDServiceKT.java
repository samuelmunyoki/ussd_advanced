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

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;

import io.flutter.Log;


/**
 * AccessibilityService object for ussd dialogs on Android mobile Telcoms
 *
 * @author Romell Dominguez
 * @version 1.1.c 27/09/2018
 * @since 1.0.a
 */
public class USSDServiceKT extends AccessibilityService {

    private static AccessibilityEvent event;
    private static final String TAG = "USSDService";

    /**
     * Catch widget by Accessibility, when is showing at mobile display
     *
     * @param event AccessibilityEvent
     */
@Override
public void onAccessibilityEvent(AccessibilityEvent event) {
    USSDServiceKT.event = event;
    USSDController ussd = USSDController.INSTANCE;
    
    // Always process the event text if it's a USSD widget
    String response = null;
    if(!event.getText().isEmpty()) {
        List<CharSequence> res = event.getText();
        res.remove("SEND");
        res.remove("CANCEL");
        res.remove("Send");
        res.remove("Cancel");
        response = String.join("\n", res);
    }

    if (isUSSDWidget(event)) {
        if (LoginView(event) && notInputText(event)) {
            // first view or logView
            clickOnButton(event, 0);
            ussd.stopRunning();
            if (response != null) {
                USSDController.callbackInvoke.over(response);
            }
        } else if (problemView(event) || LoginView(event)) {
            // deal down
            clickOnButton(event, 1);
            if (response != null) {
                USSDController.callbackInvoke.over(response);
            }
        } else if (notInputText(event)) {
            // no input panels - LAST MESSAGE
            clickOnButton(event, 0);
            ussd.stopRunning();
            if (response != null) {
                USSDController.callbackInvoke.over(response);
            }
        } else {
            // has input panel
            if (ussd.getSendType()) {
                ussd.getCallbackMessage().invoke(event);
            } else {
                USSDController.callbackInvoke.responseInvoke(event);
            }
        }
    }
}

    /**
     * Send whatever you want via USSD
     *
     * @param text any string
     */
    public static void send(String text) {
        setTextIntoField(event, text);
        clickOnButton(event, 1);
    }
    public static void send2(String text, AccessibilityEvent ev) {
        setTextIntoField(ev, text);
        try {
            clickOnButton(ev, 1);
        } catch (java.lang.Exception e) {
//            Log.d(TAG, "Error sending USSD");
//            Log.d(TAG, e.getMessage());
        }
    }

    /**
     * Dismiss dialog by using first button from USSD Dialog
     */
    public static void cancel() {
        clickOnButton(event, 0);
    }
    public static void cancel2(AccessibilityEvent ev) {
        clickOnButton(ev, 0);
    }

    /**
     * set text into input text at USSD widget
     *
     * @param event AccessibilityEvent
     * @param data  Any String
     */
    private static void setTextIntoField(AccessibilityEvent event, String data) {
        Bundle arguments = new Bundle();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, data);
            // Temporarily Trying a newer approach
//            boolean success = customSetTextIntoField(event, data);
//            if (!success) {
//                Log.e("USSD", "Failed to set text");
//            }
        }
        for (AccessibilityNodeInfo leaf : getLeaves(event)) {
            if (leaf.getClassName().equals("android.widget.EditText")
                    && !leaf.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                ClipboardManager clipboardManager = ((ClipboardManager)  USSDController
                        .INSTANCE.getContext().getSystemService(Context.CLIPBOARD_SERVICE));
                if (clipboardManager != null) {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("text", data));
                }
                leaf.performAction(AccessibilityNodeInfo.ACTION_PASTE);
            }
        }

    }

    /**
     * By Ringle (Experimental)
     * Set text into input field at USSD widget
     *
     * @param event AccessibilityEvent
     * @param data  String to set
     * @return boolean indicating success
     */
    private static boolean customSetTextIntoField(AccessibilityEvent event, String data) {
        if (event == null || data == null || event.getSource() == null) {
            return false;
        }

        AccessibilityNodeInfo source = event.getSource();
        try {
            // Find the EditText field
            AccessibilityNodeInfo inputField = findInputField(source);
            if (inputField == null) {
                return false;
            }

            // Try multiple methods to set text
            boolean success = false;

            // Method 1: Direct focus and set text (modern approach)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Bundle arguments = new Bundle();
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, data);

                // Ensure focus first
                success = inputField.performAction(AccessibilityNodeInfo.ACTION_FOCUS) &&
                        inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            }

            // Method 2: Clipboard fallback if direct set fails
            if (!success) {
                ClipboardManager clipboardManager = (ClipboardManager) USSDController
                        .INSTANCE.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboardManager != null) {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("text", data));

                    // Try focus + paste
                    success = inputField.performAction(AccessibilityNodeInfo.ACTION_FOCUS) &&
                            inputField.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                }
            }

            // Method 3: Last resort - simulate input
            if (!success && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                inputField.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, data);
                success = inputField.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args);
            }

            // Refresh the node and check if text was set
            inputField.refresh();
            CharSequence setText = inputField.getText();
            success = success || (setText != null && setText.toString().equals(data));

            return success;

        } finally {
            source.recycle();
        }
    }

    /**
     * By Ringle (Experimental)
     * Find the input field in the accessibility node tree
     */
    private static AccessibilityNodeInfo findInputField(AccessibilityNodeInfo node) {
        if (node == null) return null;

        // Check current node
        if ("android.widget.EditText".equals(node.getClassName()) && node.isEditable()) {
            return node;
        }
        // Recursively search children
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findInputField(child);
                if (result != null) {
                    return result;
                }
                child.recycle();
            }
        }
        return null;
    }

    /**
     * Method evaluate if USSD widget has input text
     *
     * @param event AccessibilityEvent
     * @return boolean has or not input text
     */
    protected static boolean notInputText(AccessibilityEvent event) {
        for (AccessibilityNodeInfo leaf : getLeaves(event))
            if (leaf.getClassName().equals("android.widget.EditText")) return false;
        return true;
    }

    /**
     * The AccessibilityEvent is instance of USSD Widget class
     *
     * @param event AccessibilityEvent
     * @return boolean AccessibilityEvent is USSD
     */
    private boolean isUSSDWidget(AccessibilityEvent event) {
        return (event.getClassName().equals("amigo.app.AmigoAlertDialog")
                || event.getClassName().equals("android.app.AlertDialog")
                || event.getClassName().equals("com.android.phone.oppo.settings.LocalAlertDialog")
                || event.getClassName().equals("com.zte.mifavor.widget.AlertDialog")
                || event.getClassName().equals("color.support.v7.app.AlertDialog"));
    }

    /**
     * The View has a login message into USSD Widget
     *
     * @param event AccessibilityEvent
     * @return boolean USSD Widget has login message
     */
    private boolean LoginView(AccessibilityEvent event) {
        return isUSSDWidget(event)
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
        if (event == null || event.getSource() == null) {
//            Log.d("TRACE:", "Event or Source is null");
            return;
        }

        List<AccessibilityNodeInfo> leaves = getLeaves(event);
//        Log.d("TRACE:", "Total nodes found: " + leaves.size());

        int count = 0;
        for (AccessibilityNodeInfo leaf : leaves) {
            if (leaf.getClassName() != null && leaf.getClassName().toString().toLowerCase().contains("button")) {
//                Log.d("TRACE:", "Button found at index " + count + ": " + leaf.getText());
                if (count == index) {
                    if (leaf.isClickable()) {
//                        Log.d("TRACE:", "Clicking on Button " + count);
                        leaf.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    } else {
//                        Log.d("TRACE:", "Button is not clickable, trying parent");
                        AccessibilityNodeInfo parent = leaf.getParent();
                        if (parent != null && parent.isClickable()) {
                            parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
//                            Log.d("TRACE:", "Clicked on parent");
                        } else {
//                            Log.d("TRACE:", "Neither button nor parent is clickable");
                        }
                    }
                    return; // Exit after clicking
                }
                count++;
            }
        }
//        Log.d("TRACE:", "No button clicked");
    }

    private static List<AccessibilityNodeInfo> getLeaves(AccessibilityEvent event) {
        List<AccessibilityNodeInfo> leaves = new ArrayList<>();
        if (event != null && event.getSource() != null) {
            getLeaves(leaves, event.getSource());
        }
        return leaves;
    }

    private static void getLeaves(List<AccessibilityNodeInfo> leaves, AccessibilityNodeInfo node) {
        if (node.getChildCount() == 0) {
            leaves.add(node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            getLeaves(leaves, node.getChild(i));
        }
    }

    /**
     * Active when SO interrupt the application
     */
    @Override
    public void onInterrupt() {
//        Log.d(TAG,  "onInterrupt");
    }

    /**
     * Configure accessibility server from Android Operative System
     */
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
//        Log.d(TAG, "onServiceConnected");
    }
}
