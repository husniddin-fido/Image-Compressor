# Google Play Store'ga Chiqarish Bo'yicha To'liq Qo'llanma (100% Tayyor)

Ushbu qo'llanmada loyihani Google Play Console orqali muvaffaqiyatli nashr qilish uchun barcha qadamlar va zarur ko'rsatmalar jamlangan.

---

## 1-BOSQICH: Release Keystore (Imzo kaliti) yaratish

Google Play faqatgina ishlab chiquvchining maxfiy kaliti bilan imzolangan `.aab` fayllarni qabul qiladi.

Terminalda loyiha ildizida turib quyidagi buyruqni bajaring:

```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias imagecompressor_key
```

Sizdan quyidagilar so'raladi:
* Parol (masalan: o'zingiz bilgan ishonchli parol)
* Ism-familiyangiz va tashkilot nomi
* Oxirida `yes` deb tasdiqlaysiz.

> ⚠️ **MUHIM:** `release.jks` faylini va uning parolini xavfsiz joyda saqlang (masalan, Google Drive yoki 1Password). Agar bu kalit yo'qolsa, kelgusida ilovaga yangilanish (update) chiqarib bo'lmaydi!

---

## 2-BOSQICH: `keystore.properties` ni sozlash

Loyihada xavfsizlik to'liq sozlangan: `release.jks` va `keystore.properties` `.gitignore` ga kiritilgan (Git'ga chiqib ketmaydi).

1. `keystore.properties.example` faylidan nusxa olib, loyiha ildizida `keystore.properties` faylini oching:
```bash
cp keystore.properties.example keystore.properties
```

2. Ichiga kalitingiz ma'lumotlarini kiriting:
```properties
storeFile=release.jks
storePassword=siz_kiritgan_parol
keyAlias=imagecompressor_key
keyPassword=siz_kiritgan_parol
```

---

## 3-BOSQICH: Imzolangan Release AAB (`.aab`) yaratish

Endi loyihaning to'liq imzolangan release versiyasini yig'amiz:

```bash
./gradlew bundleRelease
```

Yig'ish muvaffaqiyatli tugagach, yuklash uchun tayyor fayl quyidagi manzilda hosil bo'ladi:
👉 `app/build/outputs/bundle/release/app-release.aab`

Ushbu fayl Google Play Console'ga yuklanadigan asosiy fayldir (hajmi taxminan 5 MB).

---

## 4-BOSQICH: Maxfiylik Siyosati (Privacy Policy) sahifasini e'lon qilish

Google Play har bir ilova uchun jonli (web) Privacy Policy havolasini talab qiladi.

Loyihada tayyor `privacy-policy.html` mavjud. Uni bepul va tez e'lon qilish yo'llari:
1. **GitHub Pages orqali (Tavsiya etiladi):**
   - Repozitoriy: `https://github.com/husniddin-fido/Image-Compressor`
   - Sozlamalardan `Settings > Pages` bo'limiga kiring:
     * **Source:** `Deploy from a branch`
     * **Branch:** `main`
     * **Folder:** `/docs` (root emas, `/docs` papkasi tanlanadi)
     * **Save** tugmasini bosing.
   - Sizning doimiy havolangiz:
     👉 **`https://husniddin-fido.github.io/Image-Compressor/`**
     *(yoki `https://husniddin-fido.github.io/Image-Compressor/privacy-policy.html`)*
2. **Netlify yoki Vercel orqali:**
   - `privacy-policy.html` faylini bevosita Netlify Drop (drop.netlify.com) ga tashlang — 1 soniyada bepul link beradi.

---

## 5-BOSQICH: Google Play Console'da sozlash

1. **Google Play Console** (play.google.com/console) ga kiring ($25 bir martalik hisob to'lovi).
2. **Create app (Ilova yaratish)** tugmasini bosing:
   * **App name:** `Image Compressor & Resizer`
   * **Default language:** English (yoki o'zingiz xohlagan til)
   * **App or game:** App
   * **Free or paid:** Free
3. **App Content (Majburiy deklaratsiyalar)** bo'limini to'ldiring:
   * **Privacy Policy:** 4-bosqichda olingan havolani qo'ying.
   * **App Access:** `All functionality is available without special access` (login/parol yo'q).
   * **Ads:** `No, my app does not contain ads`.
   * **Content Rating (IARC):** So'rovnomani to'ldiring (hamma salbiy savollarga "No" deb javob bering, natija: PEGI 3 / Everyone).
   * **Target Audience:** 13+ (yoki All ages).
   * **Data Safety:** `PLAY_STORE_LISTING.md` faylidagi 2-bo'limga qarab to'ldiring (Ma'lumotlar to'planmaydi).
4. **Store Listing (Do'kon sahifasi)**:
   * `PLAY_STORE_LISTING.md` faylidagi tayyor Title, Short Description va Full Description matnlarini nusxalab qo'ying.
   * **App Icon:** `512 x 512 px` PNG.
   * **Feature Graphic:** `1024 x 500 px` banner.
   * **Screenshots:** Telefon uchun kamida 2 ta (tavsiya: 4-6 ta) ekran rasmini yuklang.

---

## 6-BOSQICH: Relizni yuklash va Sinov (Testing)

1. **Play Console > Testing > Closed testing** (yoki Internal testing) bo'limiga kiring.
2. **Create new release** tugmasini bosing.
3. Yuqorida yaratilgan `app-release.aab` faylini sudrab yuklang.
4. Reliz eslatmasi (Release notes) yozing:
   ```text
   Initial release of Image Compressor:
   - High quality offline image compression
   - Target size mode (KB/MB) with smart search
   - Batch processing and before/after slider
   - 100% private and on-device
   ```
5. **Save** va **Review release** tugmasini bosing.

> ℹ️ **Eslatma (2023-yil noyabrdan keyin ochilgan shaxsiy akkauntlar uchun):**
> Google Play yangi shaxsiy ishlab chiquvchilardan ilovani asosiy do'konga (Production) chiqarishdan oldin **14 kun davomida kamida 12 yoki 20 nafar tester** ishtirokida yopiq test (Closed Testing) o'tkazishni talab qiladi.

---

## 7-BOSQICH: Production (Ommaga chiqarish)

Yopiq test muddati tugagach yoki tashkilot akkauntida to'g'ridan-to'g'ri:
1. **Production > Create release** bo'limiga kiring.
2. AAB faylni yuklang va relizni tasdiqlang.
3. **Send changes for review** tugmasini bosing.
4. Google moderatsiyasi odatda 1 kundan 3 ish kunigacha ko'rib chiqadi va ilovangiz butun dunyo bo'ylab Google Play Store'da e'lon qilinadi!
