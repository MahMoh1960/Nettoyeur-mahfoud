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
        a("about", "Version 1.0 — toutes les opérations restent sur votre téléphone.",
                "الإصدار 1.0 — جميع العمليات تتم على هاتفك فقط.");
    }
}
