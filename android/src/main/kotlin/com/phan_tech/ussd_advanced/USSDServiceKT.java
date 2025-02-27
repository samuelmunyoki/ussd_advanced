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

    /**
     * Catch widget by Accessibility, when is showing at mobile display
     *
     * @param event AccessibilityEvent
     */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        USSDServiceKT.event = event;
        USSDController ussd = USSDController.INSTANCE;
        Log.d("USSDServiceKT", String.format(
            "Event Received: [type] %s [class] %s [package] %s [time] %s [text] %s",
            event.getEventType(), event.getClassName(), event.getPackageName(),
            event.getEventTime(), event.getText()));
        if (!ussd.isRunning()) {
            return;
        }else{
            // Log if ussd is running
            Log.d("USSDServiceKT", "USSDController.INSTANCE running...");
        }
        String response = null;

        // If the event has some text.
        if(!event.getText().isEmpty()) {
            List<CharSequence> res = event.getText();

            // Remove SEND and CANCEL texts from the event
            res.remove("SEND");
            res.remove("CANCEL");

            // store the event on the response variable
            response = String.join("\n", res );
        }

        if (LoginView(event) && notInputText(event)) {
            // first view or logView, do nothing, pass / FIRST MESSAGE
            Log.d("USSDServiceKT", "Login view detected. Clicking on first button.");
            clickOnButton(event, 0);
            ussd.stopRunning();
            USSDController.callbackInvoke.over(response != null ? response : "");
        } else if (problemView(event) || LoginView(event)) {
            // deal down
            Log.d("USSDServiceKT", "Problem view detected. Clicking on second button.");
            clickOnButton(event, 1);
            USSDController.callbackInvoke.over(response != null ? response : "");
        } else if (isUSSDWidget(event)) {

            Log.d("USSDServiceKT", "USSD Widget detected.");
            if (notInputText(event)) {
                // not more input panels / LAST MESSAGE
                // sent 'OK' button
                Log.d("USSDServiceKT", "No input field detected. Closing USSD.");
                clickOnButton(event, 0);
                ussd.stopRunning();
                USSDController.callbackInvoke.over(response != null ? response : "");
            } else {
                // sent option 1
                Log.d("USSDServiceKT", "Input field detected. Processing user input.");
                if (ussd.getSendType() == true)
                    ussd.getCallbackMessage().invoke(event);
                else USSDController.callbackInvoke.responseInvoke(event);
            }
        }

    }

    /**
     * Send whatever you want via USSD
     *
     * @param text any string
     */
    public static void send(String text) {
        Log.d("USSDServiceKT", "Sending USSD response: " + text);
        setTextIntoField(event, text);
        clickOnButton(event, 1);
    }
    public static void send2(String text, AccessibilityEvent ev) {
        Log.d("USSDServiceKT", "Sending USSD response: " + text);
        setTextIntoField(ev, text);
        clickOnButton(ev, 1);
    }

    /**
     * Dismiss dialog by using first button from USSD Dialog
     */
    public static void cancel() {
        Log.d("USSDServiceKT", "Clicking cancel... ");
        clickOnButton(event, 0);
    }
    public static void cancel2(AccessibilityEvent ev) {
         Log.d("USSDServiceKT", "Clicking cancel... ");
        clickOnButton(ev, 0);
    }

    /**
     * set text into input text at USSD widget
     *
     * @param event AccessibilityEvent
     * @param data  Any String
     */
    private static void setTextIntoField(AccessibilityEvent event, String data) {
        boolean textSet = false;
        Bundle arguments = new Bundle();
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, data);
        
        for (AccessibilityNodeInfo leaf : getLeaves(event)) {
            if (leaf.getClassName().toString().equals("android.widget.EditText")) {
                // Try direct text setting first
                if (leaf.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                    textSet = true;
                    Log.d("USSDServiceKT", "Text set using ACTION_SET_TEXT");
                    break;
                }
                
                // If direct setting failed, try clipboard
                try {
                    ClipboardManager clipboardManager = ((ClipboardManager) USSDController
                            .INSTANCE.getContext().getSystemService(Context.CLIPBOARD_SERVICE));
                    if (clipboardManager != null) {
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("text", data));
                        if (leaf.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                            textSet = true;
                            Log.d("USSDServiceKT", "Text set using clipboard paste");
                            break;
                        }
                    }
                } catch (Exception e) {
                    Log.e("USSDServiceKT", "Error setting text via clipboard: " + e.getMessage());
                }
            }
        }
        
        if (!textSet) {
            Log.e("USSDServiceKT", "Failed to set text in USSD dialog");
        }
    }
    // private static void setTextIntoField(AccessibilityEvent event, String data) {
    //     Bundle arguments = new Bundle();
    //     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    //         arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, data);
    //     }
    //     for (AccessibilityNodeInfo leaf : getLeaves(event)) {
    //         if (leaf.getClassName().equals("android.widget.EditText")
    //                 && !leaf.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
    //             ClipboardManager clipboardManager = ((ClipboardManager)  USSDController
    //                     .INSTANCE.getContext().getSystemService(Context.CLIPBOARD_SERVICE));
    //             if (clipboardManager != null) {
    //                 clipboardManager.setPrimaryClip(ClipData.newPlainText("text", data));
    //             }
    //             leaf.performAction(AccessibilityNodeInfo.ACTION_PASTE);
    //         }
    //     }
    // }

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
    String className = event.getClassName().toString();
    return (className.equals("amigo.app.AmigoAlertDialog")           // Generic Amigo (possibly Gionee)
            || className.equals("com.android.phone.MMIDialogActivity")
             || className.equals("com.android.phone.oppo.settings.LocalAlertDialog")
            || className.equals("android.app.AlertDialog")          // Standard Android dialog
            || className.equals("com.android.phone.DialerDialog")   // AOSP telephony dialog
            || className.equals("com.oppo.dialer.AlertDialog")      // Oppo (ColorOS) dialer dialog
            || className.equals("com.samsung.android.dialer.DialerDialog") // Samsung (One UI)
            || className.equals("com.miui.dialer.AlertDialog")      // Xiaomi (MIUI)
            || className.equals("com.vivo.dialer.AlertDialog")       // Vivo (Funtouch                                                                                                                                                                                                                                             OS)
            || className.equals("com.huawei.dialer.AlertDialog")    // Huawei (EMUI/HarmonyOS)
            || className.equals("com.google.android.dialer.DialerDialog") // Google (Pixel)
            || className.equals("com.oneplus.dialer.AlertDialog")   // OnePlus (OxygenOS)
            || className.equals("com.realme.dialer.AlertDialog")    // Realme (Realme UI)
            || className.equals("com.motorola.dialer.AlertDialog")  // Motorola
            || className.equals("com.zte.mifavor.widget.AlertDialog") // ZTE (MiFavor)
            || className.equals("color.support.v7.app.AlertDialog") // ColorOS support library
    );
}
    // private boolean isUSSDWidget(AccessibilityEvent event) {
    //     return (event.getClassName().equals("amigo.app.AmigoAlertDialog")
    //             || event.getClassName().equals("android.app.AlertDialog")
    //             || event.getClassName().equals("com.android.phone")
    //             || event.getClassName().equals("com.android.phone.oppo.settings.LocalAlertDialog")
    //             || event.getClassName().equals("com.zte.mifavor.widget.AlertDialog")
    //             || event.getClassName().equals("color.support.v7.app.AlertDialog"));
    // }

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
        List<AccessibilityNodeInfo> clickableNodes = new ArrayList<>();
        
        // First collect all clickable nodes
        for (AccessibilityNodeInfo leaf : getLeaves(event)) {
            if (leaf.isClickable() && 
                (leaf.getClassName().toString().toLowerCase().contains("button") || 
                leaf.getActionList().contains(AccessibilityNodeInfo.ACTION_CLICK))) {
                clickableNodes.add(leaf);
            }
        }
        
        // Make sure we have enough buttons and the index is valid
        if (clickableNodes.size() > index) {
            clickableNodes.get(index).performAction(AccessibilityNodeInfo.ACTION_CLICK);
            Log.d("USSDServiceKT", "Clicked on button at index: " + index);
        } else {
            Log.e("USSDServiceKT", "Button at index " + index + " not found. Total buttons: " + clickableNodes.size());
        }
    }
    // protected static void clickOnButton(AccessibilityEvent event, int index) {
    //     int count = -1;
    //     for (AccessibilityNodeInfo leaf : getLeaves(event)) {
    //         count++;
    //         if (count == index) {
    //             leaf.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    //         }
    //         if (leaf.getClassName().toString().toLowerCase().contains("button")) {

    //         }
    //     }
    // }

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
        Log.d("USSDServiceKT",  "onInterrupt");
    }

    /**
     * Configure accessibility server from Android Operative System
     */
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d("USSDServiceKT", "onServiceConnected");
    }
}
