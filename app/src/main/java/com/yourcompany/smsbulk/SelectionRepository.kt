package com.yourcompany.smsbulk

/**
 * مخزن بسيط في الذاكرة يحمل قائمة جهات الاتصال المختارة
 * لتفادي تمرير قوائم كبيرة عبر Intent (له حد أقصى بالحجم).
 * يُعاد ضبطه عند إعادة اختيار جهات الاتصال من MainActivity.
 */
object SelectionRepository {
    var selectedContacts: List<Contact> = emptyList()
}
