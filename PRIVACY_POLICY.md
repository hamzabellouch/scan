# Privacy Policy - Scan

Last Updated: October 1, 2026


### 0. Rule 0: Disclaimer on Modified Builds, External APKs & Unofficial Forks:

- **Original Source Integrity:** This privacy policy, safety guarantees, and security features apply strictly and exclusively to the official, unmodified application binaries and source code distributed directly through this official repository ([hamzabellouch/scan](https://github.com/hamzabellouch/scan)) and its verified official releases.
- **Zero Liability for Modified / External Builds:** We (the publisher and maintainer) assume **no responsibility or liability whatsoever** for any modified APKs, third-party forks, repackaged binaries, unofficial distribution channels, or altered projects not originating directly from this official repository. Any installation or use of modified, tampered, or third-party builds is entirely at your own risk, and the publisher assumes no responsibility for any consequences, damages, security compromises, or data breaches that may occur.


### 1. Executive Summary & Overview:

Scan is an Android application designed to help users scan physical documents, detect and refine page corners, apply image enhancement filters, organize scanned files locally, and convert documents on-device.
#### Core Privacy Commitment:
Scan operates "100% on-device" for document scanning, image processing, library organization, and PDF/Word/TXT conversion. We do not collect, store, transmit, share, or sell any personal data, usage logs, device identifiers, camera captures, or user documents to external analytics servers or third parties.


### 2. Information Processed On-Device:
To perform its core document scanning and conversion features, Scan reads and processes the following information `locally on your device`:

- **Camera Preview Feed:** Processed in real-time within volatile memory (RAM) strictly to detect document boundaries and capture scanned pages. Live camera frames are never recorded or transmitted.
- **Imported & Scanned Documents:** Images, PDFs, `.docx`, and `.txt` files selected or captured by the user are processed locally for corner cropping, filtering, format conversion, and export to device storage.
- **Local Library & Preferences:** Exported files and app options (such as theme preferences, scanner UI options, and language settings) are stored locally on the device using standard Android storage and `SharedPreferences`.


### 3. Permissions Used & Their Purposes:
Scan requests specific Android permissions strictly to deliver its core functionality:

**A. Camera Permission** (`android.permission.CAMERA`)
- **Purpose:** Enables the app to open the device camera for real-time document scanning via Android CameraX.
- **Scope:** Used exclusively within the interactive scanner view. Live camera stream data is processed locally.

**B. Internet & Network State** (`INTERNET` & `ACCESS_NETWORK_STATE`)
- **Purpose:** Allows Scan to check for app updates on GitHub Releases and fetch optional community language packs when requested by the user.
- **Scope:** Used strictly for update checks and language pack downloads. No telemetry, documents, or user profile data is ever sent.

**C. Request Install Packages** (`REQUEST_INSTALL_PACKAGES`)
- **Purpose:** Enables Scan to prompt the user to install updated APK packages downloaded via the built-in update checker.
- **Scope:** Used strictly when the user chooses to update the app.

**D. Post Notifications** (`POST_NOTIFICATIONS`)
- **Purpose:** Displays download progress and status notifications when fetching app updates on Android 13+.
- **Scope:** Used strictly for system notification alerts.


### 4. Camera & Document Safety Policy:
Scan complies fully with privacy guidelines regarding camera usage and local document handling:

- **Strict Camera Isolation:** The camera hardware is accessed solely when the user is on the Document Scanner screen. No background recording or silent frame capture takes place.
- **Zero Document Upload:** All scanning, OCR/text extraction, DOCX generation, and PDF rendering run strictly offline on the user's device.
- **Full User Consent & Control:** Camera access requires explicit runtime permission prompts and can be revoked at any time via Android Settings.


### 5. Data Sharing, Analytics & Advertising:

- **No Data Sharing:** No personal user data, documents, or camera feeds leave your device.
- **No Third-Party Analytics / Tracking:** Scan does not integrate third-party telemetry, tracking SDKs, or analytics services.
- **No Ads:** Scan does not display targeted or behavioral advertisements.


### 6. Data Retention & Lifecycle:

- **Volatile Frame Wiping:** All live preview frames and transient processing buffers are cleared from RAM after capture or processing.
- **Local User Control:** Uninstalling Scan removes app preferences and internal cache; exported documents saved in `Documents/Scanned` remain under the user's control unless deleted.


### 7. Managing Permissions & User Rights:
You maintain full control over the permissions granted to Scan. You may revoke any permission at any time via Android Settings:

**A. Disable Camera Permission:** `Settings > Apps > Scan > Permissions > Camera > Don't Allow`

**B. Disable Notifications:** `Settings > Apps > Scan > Notifications > Turn Off`


### 8. Contact & Support:
If you have any questions or feedback regarding this Privacy Policy or permission usage, please contact us at:
Email: hamzabellouchcontact@gmail.com


-------------------------------------------



# سياسة الخصوصية - Scan
آخر تحديث: ١ أكتوبر ٢٠٢٦

### ٠. القاعدة رقم ٠: إخلاء المسؤولية عن التطبيقات المعدلة وحزم APK الخارجية والمشاريع المشتقة:

* **سلامة المصدر الأصلي:** تنطبق سياسة الخصوصية، وضمانات الأمان، والميزات المذكورة في هذه الوثيقة حصرياً وصراحةً على الإصدارات الرسمية غير المعدلة من التطبيق والشفرة المصدرية المنشورة مباشرة عبر هذا المستودع الأصلي المعتمد ([hamzabellouch/scan](https://github.com/hamzabellouch/scan)) وصفحة إصداراته الرسمية فقط.
* **عدم تحمل المسؤولية عن النسخ المعدلة أو الخارجية:** نحن (الناشر والمطور) **لا نتحمل أي مسؤولية قانونية أو أمنية أو تقنية** عن تثبيت أو استخدام أي تطبيقات معدلة، أو حزم APK خارجية، أو نسخ مُعاد تجميعها، أو مشاريع مشتقة ومعدلة لا تنتمي لهذا المستودع الأصلي. كل ما ينتج عن استخدام أو تثبيت نسخ خارجية أو معدلة (من فقدان بيانات، أو ثغرات أمنية، أو برمجيات خبيثة، أو أي أضرار ناجمة) يقع بالكامل على عاتق المستخدم وحده دون أدنى مسؤولية على الناشر.


### ١. الملخص التنفيذي والنظرة العامة:

تطبيق Scan هو تطبيق أندرويد صُمم لمساعدة المستخدمين على مسح المستندات ضوئياً، واكتشاف الحواف وتعديلها بدقة، وتطبيق فلاتر تحسين القراءة، وتنظيم الملفات محلياً، وتحويل المستندات بالكامل على الجهاز.
الالتزام الأساسي بالخصوصية: يعمل Scan بنسبة "١٠٠٪ على الجهاز" في المسح الضوئي، ومعالجة الصور، والمكتبة المحلية، وأدوات التحويل بين PDF وWord وTXT والصور. نحن لا نجمع أو نخزن أو ننقل أو نشارك أو نبيع أي بيانات شخصية أو سجلات استخدام أو معرّفات الجهاز أو لقطات الكاميرا أو مستنداتك إلى خوادم خارجية أو أطراف ثالثة.

٢. المعلومات التي تتم معالجتها على الجهاز:
لتنفيذ ميزاته الأساسية الخاصة بمسح المستندات وتحويلها، يقوم Scan بقراءة ومعالجة المعلومات التالية "محليًا على جهازك":

* بث المعاينة من الكاميرا: يتم معالجته في الوقت الفعلي داخل الذاكرة المؤقتة (RAM) فقط لاكتشاف حواف المستند والتقاط الصفحات. لا يتم تسجيل أو نقل إطارات الكاميرا مطلقاً.
* المستندات الممسوحة والمستوردة: تتم معالجة الصور وملفات PDF و`.docx` و`.txt` التي يختارها أو يلتقطها المستخدم محلياً لقص الزوايا وتطبيق الفلاتر والتحويل والحفظ في ذاكرة الجهاز.
* المكتبة المحلية والتفضيلات: يتم حفظ الملفات المصدرة وتفضيلات التطبيق البسيطة (مثل المظهر واللغة وخيارات الماسح) محلياً على الجهاز باستخدام وحدات التخزين الرسمية و`SharedPreferences`.

٣. الأذونات المستخدمة وأغراضها:
يطلب Scan أذونات Android محددة فقط لتقديم وظائفه الأساسية:

أ. إذن الكاميرا (android.permission.CAMERA)

* الغرض: يسمح لـ Scan بفتح كاميرا الجهاز لمسح المستندات ضوئياً في الوقت الفعلي عبر مكتبة Android CameraX.
* النطاق: يُستخدم حصرياً داخل شاشة الماسح الضوئي التفاعلية. تتم معالجة بث الكاميرا محلياً بالكامل.

ب. إذن الإنترنت وحالة الشبكة (INTERNET & ACCESS_NETWORK_STATE)

* الغرض: يسمح لـ Scan بالتحقق من تحديثات التطبيق على GitHub وتنزيل حزم اللغات الاختيارية عند طلب المستخدم.
* النطاق: يُستخدم بشكل صارم للتحقق من التحديثات وتنزيل اللغات، ولا يتم إرسال أي مستندات أو بيانات تتبع أو ملفات تعريف.

ج. إذن طلب تثبيت الحزم (REQUEST_INSTALL_PACKAGES)

* الغرض: يمكّن Scan من مطالبة المستخدم بتثبيت تحديثات التطبيق التي تم تنزيلها عبر أداة التحديث المدمجة.
* النطاق: يُستخدم بشكل صارم عندما يختار المستخدم تحديث التطبيق.

د. إذن إرسال الإشعارات (POST_NOTIFICATIONS)

* الغرض: يعرض تقدم التنزيل وتنبيهات حالة تحديث التطبيق على نظام Android 13 فما فوق.
* النطاق: يُستخدم بشكل صارم لإشعارات التحديث بالنظام.

٤. سياسة أمان الكاميرا والمستندات:
يلتزم Scan التزامًا كاملًا بسياسات الخصوصية المتعلقة باستخدام الكاميرا ومعالجة الملفات:

* عزلة صارمة للكاميرا: يتم الوصول إلى الكاميرا فقط عندما يكون المستخدم في شاشة الماسح الضوئي. لا يتم أي تسجيل في الخلفية أو التقاط صامت للإطارات.
* عدم رفع المستندات: تتم جميع عمليات المسح والقص واستخراج النصوص والصور والتحويل دون اتصال بالإنترنت على جهاز المستخدم فقط.
* موافقة المستخدم والتحكم: يتطلب الوصول إلى الكاميرا إذنًا صريحًا من المستخدم، ويمكن للمستخدم إلغاؤه في أي وقت من إعدادات Android.

٥. مشاركة البيانات والتحليلات والإعلانات:

* عدم مشاركة البيانات: لا تغادر أي بيانات شخصية أو مستندات أو بث كاميرا جهازك مطلقاً.
* عدم وجود تحليلات أو تتبع من جهات خارجية: لا يدمج Scan أي خدمات Telemetry أو SDKs للتتبع أو التحليلات من جهات خارجية.
* لا توجد إعلانات: لا يعرض Scan إعلانات موجهة أو قائمة على السلوك.

٦. الاحتفاظ بالبيانات ودورة حياتها:

* مسح الإطارات المؤقتة: يتم مسح إطارات المعاينة والذاكرة المؤقتة فور انتهاء الالتقاط أو التحويل.
* تحكم المستخدم الكامل: يؤدي إلغاء تثبيت Scan إلى إزالة التفضيلات والذاكرة الداخلية المؤقتة، بينما تبقى المستندات المصدرة في مجلد `Documents/Scanned` تحت تحكمك الكامل.

٧. إدارة الأذونات وحقوق المستخدم:
تحتفظ بالتحكم الكامل في الأذونات الممنوحة لـ Scan. يمكنك إلغاء أي إذن في أي وقت عبر إعدادات Android:

أ. تعطيل إذن الكاميرا: الإعدادات > التطبيقات > Scan > الأذونات > الكاميرا > عدم السماح

ب. تعطيل الإشعارات: الإعدادات > التطبيقات > Scan > الإشعارات > إيقاف التشغيل

٨. التواصل والدعم:
إذا كانت لديك أي أسئلة أو ملاحظات بشأن سياسة الخصوصية هذه أو استخدام الأذونات، فيرجى التواصل معنا على:
البريد الإلكتروني: hamzabellouchcontact@gmail.com
