package com.yashsomani.birthdayautopilot.automation.sms

import android.app.Activity
import android.telephony.SmsManager
import com.yashsomani.birthdayautopilot.storage.database.DeliveryEvidenceClass
import org.junit.Assert.assertEquals
import org.junit.Test

class SmsCallbackEvidenceClassifierTest {
  @Test
  fun `sent callback only allowlists documented zero acceptance results`() {
    assertEquals(
      DeliveryEvidenceClass.SENT_SUCCESS,
      SmsCallbackEvidenceClassifier.sent(Activity.RESULT_OK).first,
    )
    assertEquals(
      DeliveryEvidenceClass.SENT_ZERO_ACCEPTANCE_RADIO_OFF,
      SmsCallbackEvidenceClassifier.sent(SmsManager.RESULT_ERROR_RADIO_OFF).first,
    )
    assertEquals(
      DeliveryEvidenceClass.SENT_ZERO_ACCEPTANCE_NO_SERVICE,
      SmsCallbackEvidenceClassifier.sent(SmsManager.RESULT_ERROR_NO_SERVICE).first,
    )
    assertEquals(
      DeliveryEvidenceClass.SENT_FAILURE,
      SmsCallbackEvidenceClassifier.sent(SmsManager.RESULT_ERROR_GENERIC_FAILURE).first,
    )
    assertEquals(
      DeliveryEvidenceClass.SENT_FAILURE,
      SmsCallbackEvidenceClassifier.sent(Int.MIN_VALUE).first,
    )
  }

  @Test
  fun `delivery status ranges fail closed`() {
    assertEquals(
      DeliveryEvidenceClass.DELIVERY_COMPLETE,
      SmsCallbackEvidenceClassifier.classifyStatus(0x00),
    )
    assertEquals(
      DeliveryEvidenceClass.DELIVERY_COMPLETE,
      SmsCallbackEvidenceClassifier.classifyStatus(0x1f),
    )
    assertEquals(
      DeliveryEvidenceClass.DELIVERY_PENDING,
      SmsCallbackEvidenceClassifier.classifyStatus(0x20),
    )
    assertEquals(
      DeliveryEvidenceClass.DELIVERY_PENDING,
      SmsCallbackEvidenceClassifier.classifyStatus(0x3f),
    )
    assertEquals(
      DeliveryEvidenceClass.DELIVERY_FAILED,
      SmsCallbackEvidenceClassifier.classifyStatus(0x40),
    )
    assertEquals(
      DeliveryEvidenceClass.DELIVERY_FAILED,
      SmsCallbackEvidenceClassifier.classifyStatus(0x7f),
    )
    assertEquals(
      DeliveryEvidenceClass.DELIVERY_UNKNOWN,
      SmsCallbackEvidenceClassifier.classifyStatus(-1),
    )
    assertEquals(
      DeliveryEvidenceClass.DELIVERY_UNKNOWN,
      SmsCallbackEvidenceClassifier.classifyStatus(0x80),
    )
  }

  @Test
  fun `missing or untrusted delivery material stays unknown`() {
    assertEquals(
      DeliveryEvidenceClass.DELIVERY_UNKNOWN,
      SmsCallbackEvidenceClassifier.delivery(null, "3gpp").first,
    )
    assertEquals(
      DeliveryEvidenceClass.DELIVERY_UNKNOWN,
      SmsCallbackEvidenceClassifier.delivery(byteArrayOf(1), null).first,
    )
  }
}
