package app.nettoyage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

/** Textes bilingues français / arabe. */
class Tr {
    static boolean ar = false;
    private static final HashMap<String, String[]> M = new HashMap<>();

    private static void a(String k, String fr, String arb) {
        M.put(k, new String[]{fr, arb});
    }

    static String t(String k) {
        String[] v = M.get(k);
        if (v == null) return k;
        return ar ? v[1] : v[0];
    }

    static String f(String k, Object... args) {
        return String.format(Locale.US, t(k), args);
    }

    static String size(long b) {
        String[] u = ar ? new String[]{"بايت", "ك.ب", "م.ب", "ج.ب", "ت.ب"} : new String[]{"o", "Ko", "Mo", "Go", "To"};
        if (b < 1024) return b + " " + u[0];
        double v = b;
        int i = 0;
        while (v >= 1024 && i < 4) {
            v /= 1024;
            i++;
        }
        String s = String.format(Locale.US, v >= 100 ? "%.0f" : "%.1f", v);
        if (!ar) s = s.replace('.', ',');
        return s + " " + u[i];
    }

    static String dur(long ms) {
        long m = ms / 60000;
        long h = m / 60;
        long mm = m % 60;
        if (h > 0) return h + (ar ? " س " : " h ") + mm + (ar ? " د" : " min");
        return mm + (ar ? " د" : " min");
    }

    static String date(long ms) {
        return new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(new Date(ms));
    }

    static String dateTime(long ms) {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date(ms));
    }

    static {
        a("app", "Nettoyage du téléphone", "تنظيف الهاتف");
        a("storage", "Stockage", "التخزين");
        a("used_of", "%s utilisés sur %s", "%s مستخدمة من %s");
        a("free", "%s libres", "%s متاحة");
        a("perm_title", "Autorisation requise", "إذن مطلوب");
        a("perm_msg", "Pour analyser les fichiers du téléphone, l'application a besoin de l'accès à tous les fichiers. Aucune donnée n'est envoyée sur Internet.",
                "لتحليل ملفات الهاتف يحتاج التطبيق إلى صلاحية الوصول إلى جميع الملفات. لا يتم إرسال أي بيانات عبر الإنترنت.");
        a("grant", "Accorder l'accès", "منح الإذن");
        a("scan", "Analyser le téléphone", "تحليل الهاتف");
        a("rescan", "Relancer l'analyse", "إعادة التحليل");
        a("scanning", "Analyse en cours…", "جارٍ التحليل…");
        a("scan_prog", "%d fichiers · %s", "%d ملف · %s");
        a("scan_first", "Lancez d'abord l'analyse.", "ابدأ التحليل أولاً.");
        a("cats", "Répartition des fichiers", "توزيع الملفات");
        a("c0", "Photos", "صور");
        a("c1", "Vidéos", "فيديوهات");
        a("c2", "Audio", "صوتيات");
        a("c3", "Documents", "مستندات");
        a("c4", "Applications (APK)", "تطبيقات (APK)");
        a("c5", "Archives", "أرشيفات");
        a("c6", "Autres", "أخرى");

        a("f_large", "Fichiers volumineux", "الملفات الكبيرة");
        a("f_dups", "Doublons", "الملفات المكررة");
        a("f_old", "Anciens téléchargements", "التنزيلات القديمة");
        a("f_shots", "Captures d'écran", "لقطات الشاشة");
        a("f_apk", "Fichiers APK", "ملفات APK");
        a("f_msg", "Médias de messagerie", "وسائط المراسلة");
        a("f_temp", "Fichiers temporaires", "الملفات المؤقتة");
        a("f_empty", "Dossiers et fichiers vides", "المجلدات والملفات الفارغة");
        a("f_apps", "Applications rarement utilisées", "التطبيقات النادرة الاستخدام");
        a("f_cache", "Cache", "الذاكرة المؤقتة");
        a("f_trash", "Corbeille", "سلة المحذوفات");
        a("f_report", "Rapport de nettoyage", "تقرير التنظيف");
        a("f_settings", "Réglages", "الإعدادات");
        a("f_trash_empty", "Vidage de la corbeille", "إفراغ السلة");

        a("d_large", "Les plus gros fichiers (10 Mo et plus), du plus lourd au plus léger.",
                "أكبر الملفات (10 م.ب فأكثر)، من الأثقل إلى الأخف.");
        a("d_dups", "Fichiers au contenu strictement identique. Un exemplaire est toujours conservé.",
                "ملفات متطابقة المحتوى تماماً. يتم الاحتفاظ دائماً بنسخة واحدة.");
        a("d_old", "Fichiers du dossier Téléchargements datant de plus de 30 jours.",
                "ملفات مجلد التنزيلات التي مر عليها أكثر من 30 يوماً.");
        a("d_shots", "Captures d'écran enregistrées sur le téléphone.",
                "لقطات الشاشة المحفوظة على الهاتف.");
        a("d_apk", "Fichiers d'installation d'applications, souvent inutiles après l'installation.",
                "ملفات تثبيت التطبيقات، وغالباً لا حاجة لها بعد التثبيت.");
        a("d_msg", "Photos, vidéos et audios WhatsApp/Telegram de plus de 30 jours. Les sauvegardes de discussions ne sont pas touchées.",
                "صور وفيديوهات وصوتيات واتساب/تلغرام التي مر عليها أكثر من 30 يوماً. نسخ المحادثات الاحتياطية لا تُمَسّ.");
        a("d_temp", "Fichiers .tmp, .log, .bak, miniatures et téléchargements interrompus.",
                "ملفات .tmp و .log و .bak والصور المصغّرة والتنزيلات غير المكتملة.");
        a("d_empty", "Dossiers vides et fichiers de 0 octet.",
                "المجلدات الفارغة والملفات ذات الحجم صفر.");

        a("s_large", "Plus de 10 Mo", "أكثر من 10 م.ب");
        a("s_dups", "Contenu identique", "محتوى متطابق");
        a("s_old", "Plus de 30 jours", "أقدم من 30 يوماً");
        a("s_shots", "Images d'écran", "صور الشاشة");
        a("s_apk", "Fichiers d'installation", "ملفات التثبيت");
        a("s_msg", "WhatsApp, Telegram", "واتساب، تلغرام");
        a("s_temp", "Fichiers inutiles", "ملفات غير مفيدة");
        a("s_empty", "Éléments vides", "عناصر فارغة");
        a("s_apps", "Retirer les inutilisées", "إزالة غير المستخدمة");
        a("s_cache", "Application et système", "التطبيق والنظام");
        a("s_trash", "Fichiers récupérables", "ملفات قابلة للاستعادة");
        a("s_report", "Historique", "السجل");

        a("sub_items", "%d éléments · %s", "%d عنصر · %s");
        a("dups_sub", "%d groupes · %s récupérables", "%d مجموعة · %s قابلة للتحرير");
        a("all", "Tout", "الكل");
        a("none", "Aucun", "لا شيء");
        a("selinfo", "%d sélectionné(s) · %s", "%d محدد · %s");
        a("del", "Supprimer", "حذف");
        a("confirm", "Confirmation", "تأكيد");
        a("confirm_trash", "Envoyer %d élément(s) (%s) à la corbeille ? Vous pourrez les restaurer.",
                "نقل %d عنصر (%s) إلى سلة المحذوفات؟ يمكنك استعادتها لاحقاً.");
        a("confirm_del", "Supprimer définitivement %d élément(s) (%s) ? Cette action est irréversible.",
                "حذف %d عنصر (%s) نهائياً؟ لا يمكن التراجع عن هذا الإجراء.");
        a("yes", "Oui", "نعم");
        a("no", "Non", "لا");
        a("ok", "OK", "موافق");
        a("cancel", "Annuler", "إلغاء");
        a("working", "Traitement en cours…", "جارٍ التنفيذ…");
        a("done", "%d élément(s) traité(s) · %s", "تمت معالجة %d عنصر · %s");
        a("failed", "%d échec(s)", "فشل %d");
        a("keepone", "Conservez au moins un exemplaire.", "احتفظ بنسخة واحدة على الأقل.");
        a("group_hdr", "Groupe %d · %d copies de %s", "المجموعة %d · %d نسخ بحجم %s");
        a("dups_search", "Recherche des doublons…", "جارٍ البحث عن المكررات…");
        a("empty_list", "Rien à nettoyer ici.", "لا شيء للتنظيف هنا.");
        a("file_info", "Chemin : %s\nTaille : %s\nModifié : %s", "المسار: %s\nالحجم: %s\nالتعديل: %s");

        a("usage_need", "Pour connaître la dernière utilisation de chaque application, autorisez l'accès aux statistiques d'utilisation.",
                "لمعرفة آخر استخدام لكل تطبيق، اسمح بالوصول إلى إحصائيات الاستخدام.");
        a("apps_hint", "Touchez une application pour la désinstaller. Classées de la moins récemment utilisée à la plus récente.",
                "المس تطبيقاً لإلغاء تثبيته. مرتبة من الأقدم استخداماً إلى الأحدث.");
        a("apps_loading", "Chargement des applications…", "جارٍ تحميل التطبيقات…");
        a("last_used", "Dernière utilisation : %s", "آخر استخدام: %s");
        a("never_used", "Aucune utilisation sur les 180 derniers jours", "لا استخدام خلال آخر 180 يوماً");
        a("uninstall_q", "Désinstaller « %s » ?", "إلغاء تثبيت «%s»؟");

        a("cache_info", "Android n'autorise pas une application à vider directement le cache des autres. Voici ce qui est possible.",
                "لا يسمح أندرويد لتطبيق بمسح الذاكرة المؤقتة للتطبيقات الأخرى مباشرة. هذه هي الخيارات المتاحة.");
        a("cache_app", "Cache de cette application : %s", "الذاكرة المؤقتة لهذا التطبيق: %s");
        a("cache_clear", "Vider le cache de l'application", "مسح ذاكرة التطبيق المؤقتة");
        a("cache_sys", "Libérer le cache système", "تحرير ذاكرة النظام المؤقتة");
        a("cache_sys_d", "Demande à Android de proposer le nettoyage du cache de toutes les applications (selon le modèle du téléphone).",
                "يطلب من أندرويد عرض مسح الذاكرة المؤقتة لكل التطبيقات (حسب طراز الهاتف).");
        a("open_storage", "Ouvrir les réglages de stockage", "فتح إعدادات التخزين");

        a("trash_info", "Les fichiers supprimés sont conservés %d jours avant la suppression définitive. L'espace n'est libéré qu'au vidage de la corbeille.",
                "تُحفظ الملفات المحذوفة %d يوماً قبل الحذف النهائي. لا تتحرر المساحة إلا عند إفراغ السلة.");
        a("restore", "Restaurer", "استعادة");
        a("del_forever", "Supprimer définitivement", "حذف نهائي");
        a("empty_trash", "Vider la corbeille", "إفراغ السلة");
        a("restored", "%d élément(s) restauré(s)", "تمت استعادة %d عنصر");
        a("trash_size", "Taille : %s", "الحجم: %s");
        a("confirm_empty", "Vider toute la corbeille (%s) ?", "إفراغ السلة بالكامل (%s)؟");

        a("rep_freed", "Espace libéré définitivement", "المساحة المحررة نهائياً");
        a("rep_trash", "Actuellement en corbeille", "حالياً في السلة");
        a("rep_ops", "Opérations effectuées", "العمليات المنفذة");
        a("rep_none", "Aucune opération pour le moment.", "لا توجد عمليات حتى الآن.");
        a("rep_hist", "Historique", "السجل");
        a("rep_line", "%s — %s : %d élément(s), %s (%s)", "%s — %s: %d عنصر، %s (%s)");
        a("mode_T", "corbeille", "السلة");
        a("mode_D", "définitif", "نهائي");
        a("mode_R", "restauré", "مستعاد");

        a("lang", "Langue", "اللغة");
        a("pin_title", "Code PIN (optionnel)", "رمز PIN (اختياري)");
        a("pin_off", "Désactivé", "معطّل");
        a("pin_on", "Activé", "مفعّل");
        a("pin_set", "Activer le code PIN", "تفعيل رمز PIN");
        a("pin_change", "Modifier le code", "تغيير الرمز");
        a("pin_disable", "Désactiver le code", "تعطيل الرمز");
        a("pin_new", "Nouveau code (4 à 8 chiffres)", "رمز جديد (من 4 إلى 8 أرقام)");
        a("pin_conf", "Confirmer le code", "تأكيد الرمز");
        a("pin_cur", "Code PIN actuel", "رمز PIN الحالي");
        a("pin_bad", "Code incorrect.", "الرمز غير صحيح.");
        a("pin_mismatch", "Les deux codes ne correspondent pas ou sont invalides (4 à 8 chiffres).",
                "الرمزان غير متطابقين أو غير صالحين (4 إلى 8 أرقام).");
        a("pin_saved", "Code PIN enregistré.", "تم حفظ الرمز.");
        a("pin_removed", "Code PIN désactivé.", "تم تعطيل الرمز.");
        a("pin_wait", "Trop d'essais. Réessayez dans %d s.", "محاولات كثيرة. أعد المحاولة بعد %d ث.");
        a("pin_info", "Quand il est activé, le code est demandé à l'ouverture et après 2 minutes d'absence.",
                "عند تفعيله يُطلب الرمز عند الفتح وبعد غياب دقيقتين.");
        a("lock_title", "Application verrouillée", "التطبيق مقفل");
        a("unlock", "Déverrouiller", "فتح القفل");
        a("pin_hint", "Code PIN", "رمز PIN");
        a("trash_use", "Utiliser la corbeille (recommandé)", "استخدام سلة المحذوفات (موصى به)");
        a("purge_days", "Vidage automatique après", "الإفراغ التلقائي بعد");
        a("days", "%d jours", "%d يوماً");

        a("f_similar", "Photos similaires", "الصور المتشابهة");
        a("f_blur", "Photos floues", "الصور الضبابية");
        a("f_compress", "Compression des photos", "ضغط الصور");
        a("f_folders", "Dossiers les plus lourds", "أثقل المجلدات");
        a("f_backups", "Sauvegardes (ZIP)", "النسخ الاحتياطية (ZIP)");
        a("s_similar", "Rafales et quasi-doublons", "لقطات متتالية ومتشابهة");
        a("s_blur", "Estimation automatique", "تقدير تلقائي");
        a("s_compress", "Réduire la taille", "تقليل الحجم");
        a("s_folders", "Où va l'espace ?", "أين تذهب المساحة؟");
        a("s_backups", "Copies avant suppression", "نسخ قبل الحذف");
        a("d_similar", "Photos presque identiques (même scène). La plus grande est conservée par défaut.",
                "صور شبه متطابقة (المشهد نفسه). تُحفظ الأكبر افتراضياً.");
        a("d_blur", "Photos probablement floues (estimation automatique : vérifiez avant de supprimer). Appui long pour voir les détails ou partager.",
                "صور يُحتمل أنها ضبابية (تقدير تلقائي: تحقق قبل الحذف). اضغط مطولاً للتفاصيل أو المشاركة.");
        a("d_compress", "Photos JPEG de plus de 1,5 Mo. Dates et position GPS conservées ; l'original va à la corbeille si elle est activée.",
                "صور JPEG أكبر من 1.5 م.ب. يتم الاحتفاظ بالتاريخ والموقع، وتُنقل الأصلية إلى السلة إن كانت مفعّلة.");
        a("d_backups", "Archives ZIP créées avant suppression. Utilisez 📤 pour les envoyer vers Drive ou par e-mail.",
                "أرشيفات ZIP أُنشئت قبل الحذف. استخدم 📤 لإرسالها إلى Drive أو بالبريد.");
        a("sim_hdr", "Groupe %d · %d photos similaires · %s", "المجموعة %d · %d صور متشابهة · %s");
        a("photos_search", "Analyse des photos…", "جارٍ تحليل الصور…");

        a("quick", "⚡ Nettoyage rapide", "⚡ تنظيف سريع");
        a("quick_title", "Nettoyage rapide", "تنظيف سريع");
        a("quick_msg", "Éléments sûrs sélectionnés automatiquement (%s) :", "عناصر آمنة تم تحديدها تلقائياً (%s):");
        a("quick_what", "Temporaires, fichiers vides, APK déjà installés, captures de plus de 30 jours.",
                "ملفات مؤقتة وفارغة، وAPK مثبّتة مسبقاً، ولقطات شاشة أقدم من 30 يوماً.");
        a("quick_none", "Rien de sûr à nettoyer pour le moment.", "لا توجد عناصر آمنة للتنظيف حالياً.");
        a("quick_ask", "Lancer le nettoyage ?", "بدء التنظيف؟");

        a("compress", "Compresser", "ضغط");
        a("q_high", "Qualité élevée (environ −50 %)", "جودة عالية (حوالي −50٪)");
        a("q_mid", "Qualité moyenne (environ −70 %)", "جودة متوسطة (حوالي −70٪)");
        a("compress_title", "Compresser %d photo(s)", "ضغط %d صورة");
        a("compressed", "%d photo(s) compressée(s), %s gagnés, %d ignorée(s)", "تم ضغط %d صورة، وُفّر %s، وتم تجاهل %d");

        a("share", "Partager", "مشاركة");
        a("share_max", "Sélectionnez 50 fichiers au maximum.", "اختر 50 ملفاً كحد أقصى.");
        a("zip_ok", "Sauvegarde : %s", "النسخة الاحتياطية: %s");
        a("zip_fail", "Sauvegarde ZIP impossible : suppression annulée.", "تعذّر إنشاء النسخة ZIP: تم إلغاء الحذف.");
        a("zip_note", "Une sauvegarde ZIP sera créée avant la suppression.", "سيتم إنشاء نسخة ZIP قبل الحذف.");
        a("zip_use", "Sauvegarder en ZIP avant suppression", "نسخ احتياطي ZIP قبل الحذف");
        a("zip_info", "Dossier : Téléchargements/Nettoyage-sauvegardes", "المجلد: التنزيلات/Nettoyage-sauvegardes");

        a("excl_title", "Dossiers exclus", "المجلدات المستثناة");
        a("excl_info", "Ces dossiers ne sont jamais analysés ni modifiés. Relancez l'analyse après un changement.",
                "لا يتم تحليل هذه المجلدات ولا تعديلها أبداً. أعد التحليل بعد أي تغيير.");
        a("excl_add", "Ajouter un dossier", "إضافة مجلد");
        a("excl_none", "Aucun dossier exclu.", "لا توجد مجلدات مستثناة.");
        a("excl_pick", "Exclure ce dossier", "استثناء هذا المجلد");
        a("excl_done", "Dossier exclu. Relancez l'analyse.", "تم الاستثناء. أعد التحليل.");
        a("excl_remove", "Retirer", "إزالة");
        a("excl_ask", "Exclure ce dossier de l'analyse ?", "استثناء هذا المجلد من التحليل؟");
        a("pick_empty", "Aucun sous-dossier.", "لا توجد مجلدات فرعية.");
        a("fold_info", "Touchez un dossier pour l'ouvrir, appui long pour l'exclure de l'analyse.",
                "المس مجلداً لفتحه، واضغط مطولاً لاستثنائه من التحليل.");
        a("fold_files", "Fichiers de ce dossier", "ملفات هذا المجلد");

        a("large_all", "Tous", "الكل");
        a("remind_title", "Rappel de nettoyage", "تذكير التنظيف");
        a("remind_off", "Jamais", "أبداً");
        a("remind_info", "Une notification vous indique l'espace récupérable.", "إشعار يُعلمك بالمساحة القابلة للتحرير.");
        a("remind_text", "%s peuvent être libérés.", "يمكن تحرير %s.");
        a("remind_free", "Espace libre : %s.", "المساحة المتاحة: %s.");
        a("remind_channel", "Rappels de nettoyage", "تذكيرات التنظيف");
        a("w_clean", "Nettoyer", "تنظيف");
        a("widget_info", "Widget : ajoutez « Nettoyage Téléphone » depuis les widgets de l'écran d'accueil.",
                "الويدجت: أضف «تنظيف الهاتف» من ويدجت الشاشة الرئيسية.");
        a("rep_comp", "Gain par compression", "التوفير بالضغط");
        a("mode_C", "compressé", "مضغوط");

        a("f_appcache", "Cache des applications", "ذاكرة التطبيقات المؤقتة");
        a("s_appcache", "Voir et vider une par une", "عرض ومسح تطبيقاً تلو الآخر");
        a("appcache_hint", "Classées du plus gros cache au plus petit. Touchez une application : ses informations s'ouvrent ; allez dans Stockage puis « Vider le cache » (Android interdit de le faire automatiquement).",
                "مرتبة من الأكبر إلى الأصغر. المس تطبيقاً لفتح معلوماته، ثم اذهب إلى التخزين واضغط «مسح الذاكرة المؤقتة» (يمنع أندرويد ذلك تلقائياً).");
        a("appcache_total", "Cache total des applications : %s", "إجمالي الذاكرة المؤقتة للتطبيقات: %s");
        a("appcache_none", "Aucun cache détecté.", "لم يتم العثور على ذاكرة مؤقتة.");
        a("appcache_unavail", "Les tailles de cache ne sont pas disponibles avant Android 8.", "أحجام الذاكرة المؤقتة غير متاحة قبل أندرويد 8.");
        a("f_opt", "Optimiseur", "المحسّن");
        a("s_opt", "Diagnostic et conseils", "تشخيص ونصائح");
        a("opt_score", "Note de santé du téléphone", "درجة صحة الهاتف");
        a("score_good", "Excellent", "ممتاز");
        a("score_mid", "Correct", "جيد");
        a("score_low", "À améliorer", "يحتاج تحسيناً");
        a("m_storage", "Stockage libre", "التخزين المتاح");
        a("m_ram", "Mémoire vive disponible", "الذاكرة العشوائية المتاحة");
        a("m_batt", "Batterie", "البطارية");
        a("m_up", "En marche depuis", "يعمل منذ");
        a("up_fmt", "%d j %d h", "%d يوماً %d س");
        a("opt_adv", "Conseils", "نصائح");
        a("adv_ok", "Tout est en bon état.", "كل شيء بحالة جيدة.");
        a("adv_storage", "Espace libre faible : lancez le nettoyage rapide.", "المساحة المتاحة قليلة: ابدأ التنظيف السريع.");
        a("adv_ram", "Mémoire vive très sollicitée : fermez les applications inutiles ou redémarrez.",
                "الذاكرة العشوائية مستنفدة: أغلق التطبيقات غير الضرورية أو أعد التشغيل.");
        a("adv_up", "Redémarrez le téléphone (en marche depuis %d jours) : cela libère la mémoire et corrige des lenteurs.",
                "أعد تشغيل الهاتف (يعمل منذ %d يوماً): يحرر الذاكرة ويعالج البطء.");
        a("adv_temp", "Batterie chaude (%d °C) : évitez le soleil et la charge prolongée.",
                "البطارية ساخنة (%d °م): تجنب الشمس والشحن المطوّل.");
        a("adv_health", "L'état de la batterie est à surveiller.", "حالة البطارية تستدعي المراقبة.");
        a("opt_all", "🚀 Tout optimiser", "🚀 تحسين شامل");
        a("opt_cache", "Cache de l'application vidé : %s", "تم مسح ذاكرة التطبيق المؤقتة: %s");
        a("f_hungry", "Applications gourmandes", "التطبيقات الأكثر استهلاكاً");
        a("s_hungry", "Temps d'utilisation sur 7 jours", "مدة الاستخدام خلال 7 أيام");
        a("hungry_hint", "Temps passé au premier plan ces 7 derniers jours. Touchez une application pour la forcer à s'arrêter, restreindre sa batterie ou la désinstaller.",
                "الوقت الذي قضته في الواجهة خلال 7 أيام. المس تطبيقاً لإيقافه قسراً أو تقييد بطاريته أو إلغاء تثبيته.");
        a("hungry_none", "Aucune donnée d'utilisation.", "لا توجد بيانات استخدام.");
        a("f_tips", "Conseils et raccourcis", "نصائح واختصارات");
        a("s_tips", "Batterie, fluidité, données", "البطارية والسلاسة والبيانات");
        a("tips_batt", "Économie de batterie", "توفير البطارية");
        a("tips_batt_d", "Limitez les applications actives en arrière-plan, activez le mode économie d'énergie, réduisez la luminosité et le délai de mise en veille.",
                "قلّل التطبيقات العاملة في الخلفية، فعّل وضع توفير الطاقة، وخفّض السطوع ومهلة إطفاء الشاشة.");
        a("b_opt", "Optimisation de la batterie", "تحسين البطارية");
        a("b_saver", "Mode économie d'énergie", "وضع توفير الطاقة");
        a("b_display", "Affichage (luminosité, veille)", "العرض (السطوع، السكون)");
        a("tips_smooth", "Fluidité", "السلاسة");
        a("tips_smooth_d", "Pour un téléphone plus réactif : activez les options pour développeurs (À propos du téléphone, puis touchez 7 fois « Numéro de build »), puis réglez les trois échelles d'animation sur 0,5×.",
                "لهاتف أكثر استجابة: فعّل خيارات المطور (حول الهاتف ثم المس «رقم الإصدار» 7 مرات)، ثم اضبط مقاييس الرسوم المتحركة الثلاثة على 0.5×.");
        a("b_about", "À propos du téléphone", "حول الهاتف");
        a("b_dev", "Options pour développeurs", "خيارات المطور");
        a("tips_data", "Données en arrière-plan", "البيانات في الخلفية");
        a("tips_data_d", "Activez l'économiseur de données et limitez les données en arrière-plan des applications peu utiles.",
                "فعّل موفّر البيانات وقيّد بيانات الخلفية للتطبيقات قليلة الفائدة.");
        a("b_data", "Utilisation des données", "استخدام البيانات");
        a("setting_fail", "Réglage introuvable sur ce téléphone.", "الإعداد غير متوفر على هذا الهاتف.");
        a("reboot_tip", "Pensez à redémarrer le téléphone.", "فكّر في إعادة تشغيل الهاتف.");
        a("reboot_banner", "Le téléphone fonctionne depuis %d jours : un redémarrage libère la mémoire et corrige les lenteurs.",
                "الهاتف يعمل منذ %d يوماً: إعادة التشغيل تحرر الذاكرة وتعالج البطء.");
        a("about", "Version 1.0 — toutes les opérations restent sur votre téléphone.",
                "الإصدار 1.0 — جميع العمليات تتم على هاتفك فقط.");
    }
}
