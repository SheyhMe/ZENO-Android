package com.zeno.assistant

import java.util.Locale

/**
 * Small offline-first cybersecurity learning library.
 * Covers defensive security and authorized practice only.
 */
object CyberSecurityKnowledge {
    val overview = """
KIBERXAVFSIZLIK — ZENO O‘QUV BAZASI

1. Asoslar
Kiberxavfsizlik — qurilmalar, akkauntlar, tarmoqlar va ma’lumotlarni ruxsatsiz kirish, o‘zgartirish yoki yo‘qotishdan himoyalash. Asosiy tamoyillar: maxfiylik, yaxlitlik va mavjudlik.

2. Parol va akkaunt himoyasi
Har bir xizmat uchun alohida, uzun parol ishlating. Parol menejeridan foydalaning va imkon bo‘lsa passkey yoki ikki bosqichli tasdiqlashni (MFA) yoqing. SMS-kod va tasdiqlash kodlarini hech kimga bermang.

3. Phishing va ijtimoiy muhandislik
Shoshiltiruvchi xabarlar, sovrin va’dalari, soxta havolalar va noma’lum APK fayllardan ehtiyot bo‘ling. Domen nomini tekshiring. Kutilmagan xabardagi havolaga bosish o‘rniga xizmatning rasmiy ilovasini oching.

4. Android xavfsizligi
Ilovalarni faqat ishonchli manbadan o‘rnating. Keraksiz Accessibility, SMS, mikrofon, kamera va joylashuv ruxsatlarini bermang. Android va ilovalarni yangilang. Play Protect tekshiruvini yoqib qo‘ying.

5. Wi‑Fi va tarmoq
Router parolini yangilang, WPA2-AES yoki WPA3 dan foydalaning, WPS ni o‘chiring va router firmware’ini yangilang. Ochiq Wi‑Fi’da maxfiy amallarni bajarmang.

6. Linux va terminal
Linux fayl tizimi, foydalanuvchi huquqlari, jarayonlar, tarmoq buyruqlari va loglarni o‘rganing. Buyruqlarni faqat o‘zingizga tegishli qurilma yoki ruxsat berilgan laboratoriyada sinang.

7. Web xavfsizligi
OWASP Top 10 mavzularini o‘rganing: access control xatolari, injection, autentifikatsiya xatolari, xavfsiz konfiguratsiya va ma’lumotlarni himoyalash. Mashqlarni faqat maxsus test muhitida bajaring.

8. Hodisaga javob
Shubhali ilovani internetdan uzing, noma’lum ruxsatlarni bekor qiling, ishonchli qurilmadan parollarni almashtiring, MFA ni yoqing va muhim fayllarning zaxira nusxasini saqlang.

O‘rganish yo‘li:
Kompyuter va tarmoq asoslari → Linux → Python asoslari → web xavfsizligi → OWASP → ruxsatli laboratoriyalar → log tahlili va himoya.

ZENO kiberxavfsizlik bo‘yicha tushuntirish, xavfsiz sozlash va qonuniy laboratoriya mashqlarida yordam beradi. Begona tizimlarga ruxsatsiz kirish yoki zarar yetkazish bo‘yicha ko‘rsatma bermaydi.
""".trimIndent()

    fun answer(raw: String): String? {
        val q = raw.lowercase(Locale.ROOT)
        val cyberTerms = listOf(
            "kiber", "xavfsizlik", "xaker", "hacker", "pentest", "penetration",
            "phishing", "fishing", "virus", "malware", "zararli dastur", "parol",
            "password", "mfa", "2fa", "linux", "owasp", "wi-fi", "wifi",
            "tarmoq", "network", "android xavfsiz", "sql injection", "xss"
        )
        if (cyberTerms.none { q.contains(it) }) return null

        return when {
            listOf("phishing", "fishing", "soxta havola", "aldov").any { q.contains(it) } ->
                "PHISHINGDAN HIMOYALANISH\n1. Yuboruvchining manzilini tekshiring.\n2. Domen nomidagi harf almashishlariga e’tibor bering.\n3. Kutilmagan fayl yoki APK’ni ochmang.\n4. Parol yoki SMS-kodni xabarda yubormang.\n5. Havolani bosish o‘rniga rasmiy ilovani mustaqil oching.\nAgar havolaga ma’lumot kiritgan bo‘lsangiz, ishonchli qurilmadan parolni almashtiring va MFA’ni yoqing."
            listOf("parol", "password", "mfa", "2fa", "akkaunt").any { q.contains(it) } ->
                "AKKAUNT HIMOYASI\n• Har bir sayt uchun boshqa, uzun parol ishlating.\n• Parol menejeridan foydalaning.\n• Passkey yoki authenticator orqali MFA yoqing.\n• Tiklash emaili va telefon raqamini yangilang.\n• Tasdiqlash kodlarini hech kimga bermang."
            listOf("android", "telefon", "apk", "ruxsat").any { q.contains(it) } ->
                "ANDROID XAVFSIZLIGI\n• APK’larni noma’lum saytlardan o‘rnatmang.\n• Accessibility, SMS, mikrofon va kamera ruxsatlarini tekshiring.\n• Android va ilovalarni yangilang.\n• Play Protect’ni yoqing.\n• Noma’lum profil, VPN yoki administrator ilovalarini tekshiring."
            listOf("wifi", "wi-fi", "router", "tarmoq", "network").any { q.contains(it) } ->
                "WI-FI XAVFSIZLIGI\n• WPA2-AES yoki WPA3 dan foydalaning.\n• Router admin parolini almashtiring.\n• WPS’ni o‘chiring.\n• Router firmware’ini yangilang.\n• Mehmonlar uchun alohida guest network yarating."
            listOf("linux", "terminal", "ubuntu", "kali").any { q.contains(it) } ->
                "LINUXNI XAVFSIZ O‘RGANISH\nAvval terminal, kataloglar, fayl huquqlari, foydalanuvchilar, jarayonlar va loglarni o‘rganing. Keyin ip, ss, ping, curl va journalctl kabi diagnostika buyruqlarini sinang. Kali Linux vositalarini faqat o‘zingizga tegishli yoki yozma ruxsat berilgan laboratoriyada ishlating."
            listOf("owasp", "sql injection", "xss", "web").any { q.contains(it) } ->
                "WEB XAVFSIZLIGI\nOWASP Top 10 dan boshlang: access control, injection, authentication failures, security misconfiguration va cryptographic failures. SQL injection va XSS’ni faqat DVWA, Juice Shop kabi maxsus laboratoriyalarda mashq qiling; haqiqiy saytlarda ruxsatsiz test qilmang."
            else -> overview
        }
    }
}
