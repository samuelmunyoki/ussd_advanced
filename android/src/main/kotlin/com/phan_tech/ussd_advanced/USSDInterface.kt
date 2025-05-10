/*
 * Copyright (c) 2020. BoostTag E.I.R.L. Romell D.Z.
 * All rights reserved
 * porfile.romellfudi.com
 */

/**
 * BoostTag E.I.R.L. All Copyright Reserved
 * www.boosttag.com
 */
package com.phan_tech.ussd_advanced

import android.view.accessibility.AccessibilityEvent

/**
 * Interface ussd handler
 *
 * @author Romell Dominguez
 * @version 1.1.i 2019/04/18
 * @since 1.1.i
 */

interface USSDInterface {
    fun sendData(text: String)
    fun sendData2(text: String, event: AccessibilityEvent)
    fun stopRunning()
    fun onResponseReceived(response: String)
}
