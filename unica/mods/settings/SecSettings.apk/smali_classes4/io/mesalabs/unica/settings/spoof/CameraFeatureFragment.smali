.class public Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;
.super Lcom/android/settings/dashboard/DashboardFragment;
.source "CameraFeatureFragment.java"


# static fields
.field public static final SEARCH_INDEX_DATA_PROVIDER:Lcom/android/settings/search/BaseSearchIndexProvider;


# instance fields
.field public mExportLauncher:Landroidx/activity/result/ActivityResultLauncher;

.field public mExpandedCategory:Ljava/lang/String;

.field public mImportLauncher:Landroidx/activity/result/ActivityResultLauncher;

.field public mCurrentCategory:Ljava/lang/String;

.field public mSearchQuery:Ljava/lang/String;


# direct methods
.method public static constructor <clinit>()V
    .locals 3

    new-instance v0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$1;

    const-string v1, "xml"

    const-string v2, "unica_camera_feature_settings"

    invoke-static {v1, v2}, Lio/mesalabs/unica/utils/Utils;->getResourceId(Ljava/lang/String;Ljava/lang/String;)I

    move-result v1

    invoke-direct {v0, v1}, Lcom/android/settings/search/BaseSearchIndexProvider;-><init>(I)V

    sput-object v0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->SEARCH_INDEX_DATA_PROVIDER:Lcom/android/settings/search/BaseSearchIndexProvider;

    return-void
.end method

.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Lcom/android/settings/dashboard/DashboardFragment;-><init>()V

    return-void
.end method

.method public static buildKey(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
    .locals 2

    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "unica_camera_feature:"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string p0, ":"

    invoke-virtual {v0, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    return-object p0
.end method

.method public static buildPreferenceControllers$1(Landroid/content/Context;Landroidx/fragment/app/Fragment;)Ljava/util/List;
    .locals 0

    new-instance p0, Ljava/util/ArrayList;

    invoke-direct {p0}, Ljava/util/ArrayList;-><init>()V

    return-object p0
.end method

.method public static buildSummary(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
    .locals 2

    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v0, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string p0, " = "

    invoke-virtual {v0, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    return-object p0
.end method

.method public static buildTitle(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
    .locals 2

    const-string v0, "value"

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_0

    const-string v0, "enable"

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_1

    :cond_0
    return-object p0

    :cond_1
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v0, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string p0, " / "

    invoke-virtual {v0, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    return-object p0
.end method

.method public static containsIgnoreCase(Ljava/lang/String;Ljava/lang/String;)Z
    .locals 1

    if-eqz p0, :cond_0

    invoke-virtual {p0}, Ljava/lang/String;->toLowerCase()Ljava/lang/String;

    move-result-object p0

    invoke-virtual {p0, p1}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z

    move-result p0

    return p0

    :cond_0
    const/4 p0, 0x0

    return p0
.end method

.method public static matchesSearch(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z
    .locals 1

    invoke-static {p0}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z

    move-result v0

    if-eqz v0, :cond_0

    const/4 p0, 0x1

    return p0

    :cond_0
    invoke-virtual {p0}, Ljava/lang/String;->toLowerCase()Ljava/lang/String;

    move-result-object p0

    invoke-static {p1, p0}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->containsIgnoreCase(Ljava/lang/String;Ljava/lang/String;)Z

    move-result v0

    if-nez v0, :cond_1

    invoke-static {p2, p0}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->containsIgnoreCase(Ljava/lang/String;Ljava/lang/String;)Z

    move-result v0

    if-nez v0, :cond_1

    invoke-static {p3, p0}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->containsIgnoreCase(Ljava/lang/String;Ljava/lang/String;)Z

    move-result v0

    if-nez v0, :cond_1

    invoke-static {p4, p0}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->containsIgnoreCase(Ljava/lang/String;Ljava/lang/String;)Z

    move-result v0

    if-nez v0, :cond_1

    goto :cond_2

    :cond_1
    const/4 p0, 0x1

    return p0

    :cond_2
    const/4 p0, 0x0

    return p0
.end method


# virtual methods
.method public final createPreferenceControllers(Landroid/content/Context;)Ljava/util/List;
    .locals 0

    invoke-static {p1, p0}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->buildPreferenceControllers$1(Landroid/content/Context;Landroidx/fragment/app/Fragment;)Ljava/util/List;

    move-result-object p0

    return-object p0
.end method

.method public final getLogTag()Ljava/lang/String;
    .locals 0

    const-string p0, "CameraFeatureFragment"

    return-object p0
.end method

.method public final getMetricsCategory()I
    .locals 0

    const/16 p0, 0x2e8

    return p0
.end method

.method public final getPreferenceScreenResId()I
    .locals 1

    const-string p0, "xml"

    const-string v0, "unica_camera_feature_settings"

    invoke-static {p0, v0}, Lio/mesalabs/unica/utils/Utils;->getResourceId(Ljava/lang/String;Ljava/lang/String;)I

    move-result p0

    return p0
.end method

.method public final onCreate(Landroid/os/Bundle;)V
    .locals 4

    invoke-super {p0, p1}, Lcom/android/settings/dashboard/DashboardFragment;->onCreate(Landroid/os/Bundle;)V

    const/4 p1, 0x1

    invoke-virtual {p0, p1}, Lcom/android/settings/SettingsPreferenceFragment;->setAnimationAllowed(Z)V

    invoke-virtual {p0}, Landroidx/fragment/app/Fragment;->getArguments()Landroid/os/Bundle;

    move-result-object v0

    if-eqz v0, :cond_no_category_arg

    const-string v1, "camera_feature_category"

    invoke-virtual {v0, v1}, Landroid/os/Bundle;->getString(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->mExpandedCategory:Ljava/lang/String;

    :cond_no_category_arg
    new-instance v0, Landroidx/activity/result/contract/ActivityResultContracts$StartActivityForResult;

    invoke-direct {v0}, Landroidx/activity/result/contract/ActivityResultContracts$StartActivityForResult;-><init>()V

    new-instance v1, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ImportCallback;

    invoke-direct {v1, p0}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ImportCallback;-><init>(Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;)V

    invoke-virtual {p0, v0, v1}, Landroidx/fragment/app/Fragment;->registerForActivityResult(Landroidx/activity/result/contract/ActivityResultContract;Landroidx/activity/result/ActivityResultCallback;)Landroidx/activity/result/ActivityResultLauncher;

    move-result-object v2

    iput-object v2, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->mImportLauncher:Landroidx/activity/result/ActivityResultLauncher;

    new-instance v0, Landroidx/activity/result/contract/ActivityResultContracts$StartActivityForResult;

    invoke-direct {v0}, Landroidx/activity/result/contract/ActivityResultContracts$StartActivityForResult;-><init>()V

    new-instance v1, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ExportCallback;

    invoke-direct {v1, p0}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ExportCallback;-><init>(Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;)V

    invoke-virtual {p0, v0, v1}, Landroidx/fragment/app/Fragment;->registerForActivityResult(Landroidx/activity/result/contract/ActivityResultContract;Landroidx/activity/result/ActivityResultCallback;)Landroidx/activity/result/ActivityResultLauncher;

    move-result-object v0

    iput-object v0, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->mExportLauncher:Landroidx/activity/result/ActivityResultLauncher;

    return-void
.end method

.method public final onPreferenceTreeClick(Landroidx/preference/Preference;)Z
    .locals 7

    invoke-virtual {p1}, Landroidx/preference/Preference;->getKey()Ljava/lang/String;

    move-result-object v0

    const/4 v2, 0x1

    const-string v1, "unica_camera_feature_search"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_search

    invoke-virtual {p0}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->showSearchDialog()V

    return v2

    :cond_search
    const-string v1, "unica_camera_feature_import"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1

    iget-object p1, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->mImportLauncher:Landroidx/activity/result/ActivityResultLauncher;

    if-eqz p1, :cond_0

    invoke-virtual {p0}, Lcom/android/settings/core/InstrumentedPreferenceFragment;->getPrefContext()Landroid/content/Context;

    move-result-object p0

    const-string v0, "application/xml"

    const-string v1, "text/xml"

    filled-new-array {v0, v1}, [Ljava/lang/String;

    move-result-object v0

    const-string v1, "xml"

    filled-new-array {v1}, [Ljava/lang/String;

    move-result-object v1

    invoke-static {p0, v0, v1}, Lio/mesalabs/unica/utils/Utils;->getFilePickerOpenIntent(Landroid/content/Context;[Ljava/lang/String;[Ljava/lang/String;)Landroid/content/Intent;

    move-result-object p0

    invoke-virtual {p1, p0}, Landroidx/activity/result/ActivityResultLauncher;->launch(Ljava/lang/Object;)V

    :cond_0
    return v2

    :cond_1
    const-string v1, "unica_camera_feature_export"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3

    iget-object p0, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->mExportLauncher:Landroidx/activity/result/ActivityResultLauncher;

    if-eqz p0, :cond_2

    new-instance p1, Landroid/content/Intent;

    const-string v0, "android.intent.action.CREATE_DOCUMENT"

    invoke-direct {p1, v0}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    const-string v0, "android.intent.category.OPENABLE"

    invoke-virtual {p1, v0}, Landroid/content/Intent;->addCategory(Ljava/lang/String;)Landroid/content/Intent;

    const-string v0, "application/xml"

    invoke-virtual {p1, v0}, Landroid/content/Intent;->setType(Ljava/lang/String;)Landroid/content/Intent;

    const-string v0, "android.intent.extra.TITLE"

    const-string v1, "camera-feature.xml"

    invoke-virtual {p1, v0, v1}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    invoke-virtual {p0, p1}, Landroidx/activity/result/ActivityResultLauncher;->launch(Ljava/lang/Object;)V

    :cond_2
    return v2

    :cond_3
    const-string v1, "unica_camera_feature_reset"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_5

    invoke-virtual {p0}, Lcom/android/settings/core/InstrumentedPreferenceFragment;->getPrefContext()Landroid/content/Context;

    move-result-object p1

    invoke-static {p1}, Lio/mesalabs/unica/settings/spoof/CameraFeatureUtils;->resetOverride(Landroid/content/Context;)Z

    move-result p1

    if-eqz p1, :cond_4

    const-string p1, "Camera feature override reset."

    invoke-virtual {p0, p1}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->showToast(Ljava/lang/String;)V

    invoke-virtual {p0}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->rebuildFeaturePreferences()V

    goto :goto_0

    :cond_4
    const-string p1, "Could not reset camera feature override."

    invoke-virtual {p0, p1}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->showToast(Ljava/lang/String;)V

    :goto_0
    return v2

    :cond_5
    invoke-super {p0, p1}, Lcom/android/settings/dashboard/DashboardFragment;->onPreferenceTreeClick(Landroidx/preference/Preference;)Z

    move-result p0

    return p0
.end method

.method public final onResume()V
    .locals 0

    invoke-super {p0}, Lcom/android/settings/dashboard/DashboardFragment;->onResume()V

    invoke-virtual {p0}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->rebuildFeaturePreferences()V

    return-void
.end method

.method public final rebuildFeaturePreferences()V
    .locals 15

    const-string v0, "unica_camera_feature_dynamic"

    invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->findPreference(Ljava/lang/CharSequence;)Landroidx/preference/Preference;

    move-result-object v0

    check-cast v0, Landroidx/preference/PreferenceGroup;

    if-eqz v0, :cond_7

    invoke-virtual {v0}, Landroidx/preference/PreferenceGroup;->removeAll()V

    iget-object v2, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->mExpandedCategory:Ljava/lang/String;

    invoke-static {v2}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z

    move-result v2

    const-string v3, "unica_camera_feature_import"

    invoke-virtual {p0, v3}, Landroidx/preference/PreferenceFragmentCompat;->findPreference(Ljava/lang/CharSequence;)Landroidx/preference/Preference;

    move-result-object v3

    if-eqz v3, :cond_visibility_import_done

    invoke-virtual {v3, v2}, Landroidx/preference/Preference;->setVisible(Z)V

    :cond_visibility_import_done
    const-string v3, "unica_camera_feature_export"

    invoke-virtual {p0, v3}, Landroidx/preference/PreferenceFragmentCompat;->findPreference(Ljava/lang/CharSequence;)Landroidx/preference/Preference;

    move-result-object v3

    if-eqz v3, :cond_visibility_export_done

    invoke-virtual {v3, v2}, Landroidx/preference/Preference;->setVisible(Z)V

    :cond_visibility_export_done
    const-string v3, "unica_camera_feature_reset"

    invoke-virtual {p0, v3}, Landroidx/preference/PreferenceFragmentCompat;->findPreference(Ljava/lang/CharSequence;)Landroidx/preference/Preference;

    move-result-object v3

    if-eqz v3, :cond_visibility_reset_done

    invoke-virtual {v3, v2}, Landroidx/preference/Preference;->setVisible(Z)V

    :cond_visibility_reset_done
    const-string v3, "unica_camera_feature_search"

    invoke-virtual {p0, v3}, Landroidx/preference/PreferenceFragmentCompat;->findPreference(Ljava/lang/CharSequence;)Landroidx/preference/Preference;

    move-result-object v3

    if-eqz v3, :cond_visibility_search_done

    invoke-virtual {v3, v2}, Landroidx/preference/Preference;->setVisible(Z)V

    :cond_visibility_search_done
    const-string v3, "unica_camera_feature_actions_divider"

    invoke-virtual {p0, v3}, Landroidx/preference/PreferenceFragmentCompat;->findPreference(Ljava/lang/CharSequence;)Landroidx/preference/Preference;

    move-result-object v3

    if-eqz v3, :cond_visibility_divider_done

    invoke-virtual {v3, v2}, Landroidx/preference/Preference;->setVisible(Z)V

    :cond_visibility_divider_done
    if-nez v2, :cond_category_title_done

    iget-object v3, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->mExpandedCategory:Ljava/lang/String;

    invoke-virtual {v0, v3}, Landroidx/preference/Preference;->setTitle(Ljava/lang/CharSequence;)V

    invoke-virtual {p0}, Landroidx/preference/PreferenceFragmentCompat;->getPreferenceScreen()Landroidx/preference/PreferenceScreen;

    move-result-object v4

    if-eqz v4, :cond_category_title_done

    invoke-virtual {v4, v3}, Landroidx/preference/Preference;->setTitle(Ljava/lang/CharSequence;)V

    :cond_category_title_done
    const-string v1, "unica_camera_feature_search"

    invoke-virtual {p0, v1}, Landroidx/preference/PreferenceFragmentCompat;->findPreference(Ljava/lang/CharSequence;)Landroidx/preference/Preference;

    move-result-object v1

    if-eqz v1, :cond_search_summary_done

    iget-object v2, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->mSearchQuery:Ljava/lang/String;

    invoke-static {v2}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z

    move-result v3

    if-eqz v3, :cond_search_summary_filtering

    const-string v2, "Filter by feature name, attribute, or value."

    goto :goto_search_summary

    :cond_search_summary_filtering
    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    const-string v5, "Filtering: "

    invoke-virtual {v3, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    :goto_search_summary
    invoke-virtual {v1, v2}, Landroidx/preference/Preference;->setSummary(Ljava/lang/CharSequence;)V

    :cond_search_summary_done
    invoke-virtual {p0}, Lcom/android/settings/core/InstrumentedPreferenceFragment;->getPrefContext()Landroid/content/Context;

    move-result-object v1

    invoke-static {}, Lio/mesalabs/unica/settings/spoof/CameraFeatureUtils;->parseFeatureRows()Ljava/util/ArrayList;

    move-result-object v2

    invoke-virtual {v2}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;

    move-result-object v3

    const/4 v4, 0x0

    const/4 v5, 0x0

    iput-object v5, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->mCurrentCategory:Ljava/lang/String;

    :cond_0
    :goto_0
    invoke-interface {v3}, Ljava/util/Iterator;->hasNext()Z

    move-result v5

    if-eqz v5, :cond_7

    invoke-interface {v3}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v5

    check-cast v5, Ljava/util/Map;

    const-string v6, "__category"

    invoke-interface {v5, v6}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v6

    check-cast v6, Ljava/lang/String;

    if-eqz v6, :cond_feature_row

    iput-object v6, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->mCurrentCategory:Ljava/lang/String;

    iget-object v7, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->mExpandedCategory:Ljava/lang/String;

    invoke-static {v7}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z

    move-result v7

    if-eqz v7, :goto_0

    iget-object v7, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->mSearchQuery:Ljava/lang/String;

    invoke-static {v7}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z

    move-result v7

    if-eqz v7, :goto_0

    new-instance v7, Landroidx/preference/SecPreference;

    const/4 v8, 0x0

    invoke-direct {v7, v1, v8}, Landroidx/preference/SecPreference;-><init>(Landroid/content/Context;Landroid/util/AttributeSet;)V

    const-string v8, "__category"

    invoke-static {v8, v6}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->buildKey(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v8

    invoke-virtual {v7, v8}, Landroidx/preference/Preference;->setKey(Ljava/lang/String;)V

    invoke-virtual {v7, v6}, Landroidx/preference/Preference;->setTitle(Ljava/lang/CharSequence;)V

    const-string v8, "Open local name values"

    invoke-virtual {v7, v8}, Landroidx/preference/Preference;->setSummary(Ljava/lang/CharSequence;)V

    invoke-virtual {v7, v4}, Landroidx/preference/Preference;->setOrder(I)V

    add-int/lit8 v4, v4, 0x1

    const-string v8, "io.mesalabs.unica.settings.spoof.CameraFeatureFragment"

    invoke-virtual {v7, v8}, Landroidx/preference/Preference;->setFragment(Ljava/lang/String;)V

    invoke-virtual {v7}, Landroidx/preference/Preference;->getExtras()Landroid/os/Bundle;

    move-result-object v8

    const-string v9, "camera_feature_category"

    invoke-virtual {v8, v9, v6}, Landroid/os/Bundle;->putString(Ljava/lang/String;Ljava/lang/String;)V

    invoke-virtual {v0, v7}, Landroidx/preference/PreferenceGroup;->addPreference(Landroidx/preference/Preference;)Z

    goto :goto_0

    :cond_feature_row
    const-string v6, "name"

    invoke-interface {v5, v6}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v6

    check-cast v6, Ljava/lang/String;

    if-eqz v6, :cond_0

    invoke-interface {v5}, Ljava/util/Map;->entrySet()Ljava/util/Set;

    move-result-object v7

    invoke-interface {v7}, Ljava/util/Set;->iterator()Ljava/util/Iterator;

    move-result-object v7

    :cond_1
    :goto_1
    invoke-interface {v7}, Ljava/util/Iterator;->hasNext()Z

    move-result v8

    if-eqz v8, :cond_0

    invoke-interface {v7}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v8

    check-cast v8, Ljava/util/Map$Entry;

    invoke-interface {v8}, Ljava/util/Map$Entry;->getKey()Ljava/lang/Object;

    move-result-object v9

    check-cast v9, Ljava/lang/String;

    const-string v10, "name"

    invoke-virtual {v10, v9}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v10

    if-nez v10, :cond_1

    invoke-interface {v8}, Ljava/util/Map$Entry;->getValue()Ljava/lang/Object;

    move-result-object v10

    check-cast v10, Ljava/lang/String;

    if-eqz v10, :cond_1

    invoke-static {v6, v9}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->buildTitle(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v11

    invoke-static {v9, v10}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->buildSummary(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v12

    iget-object v13, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->mSearchQuery:Ljava/lang/String;

    invoke-static {v13}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z

    move-result v14

    if-eqz v14, :cond_search_filter

    iget-object v14, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->mCurrentCategory:Ljava/lang/String;

    if-eqz v14, :cond_show_feature

    iget-object v13, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->mExpandedCategory:Ljava/lang/String;

    if-eqz v13, :cond_1

    invoke-virtual {v13, v14}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v14

    if-eqz v14, :cond_1

    goto :cond_show_feature

    :cond_search_filter
    invoke-static {v13, v6, v9, v10, v12}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->matchesSearch(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z

    move-result v14

    if-eqz v14, :cond_1

    :cond_show_feature
    invoke-static {v10}, Lio/mesalabs/unica/settings/spoof/CameraFeatureUtils;->isBooleanValue(Ljava/lang/String;)Z

    move-result v13

    if-eqz v13, :cond_4

    new-instance v13, Landroidx/preference/SecSwitchPreference;

    invoke-direct {v13, v1}, Landroidx/preference/SecSwitchPreference;-><init>(Landroid/content/Context;)V

    invoke-static {v6, v9}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->buildKey(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v14

    invoke-virtual {v13, v14}, Landroidx/preference/Preference;->setKey(Ljava/lang/String;)V

    invoke-virtual {v13, v11}, Landroidx/preference/Preference;->setTitle(Ljava/lang/CharSequence;)V

    invoke-virtual {v13, v12}, Landroidx/preference/Preference;->setSummary(Ljava/lang/CharSequence;)V

    invoke-virtual {v13, v4}, Landroidx/preference/Preference;->setOrder(I)V

    add-int/lit8 v4, v4, 0x1

    invoke-static {v10}, Ljava/lang/Boolean;->parseBoolean(Ljava/lang/String;)Z

    move-result v14

    invoke-virtual {v13, v14}, Landroidx/preference/TwoStatePreference;->setChecked(Z)V

    new-instance v14, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ToggleChangeListener;

    invoke-direct {v14, p0, v1, v6, v9}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ToggleChangeListener;-><init>(Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)V

    invoke-virtual {v13, v14}, Landroidx/preference/Preference;->setOnPreferenceChangeListener(Landroidx/preference/Preference$OnPreferenceChangeListener;)V

    invoke-virtual {v0, v13}, Landroidx/preference/PreferenceGroup;->addPreference(Landroidx/preference/Preference;)Z

    goto :goto_1

    :cond_4
    new-instance v13, Landroidx/preference/SecPreference;

    const/4 v14, 0x0

    invoke-direct {v13, v1, v14}, Landroidx/preference/SecPreference;-><init>(Landroid/content/Context;Landroid/util/AttributeSet;)V

    invoke-static {v6, v9}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->buildKey(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v14

    invoke-virtual {v13, v14}, Landroidx/preference/Preference;->setKey(Ljava/lang/String;)V

    invoke-virtual {v13, v11}, Landroidx/preference/Preference;->setTitle(Ljava/lang/CharSequence;)V

    invoke-virtual {v13, v12}, Landroidx/preference/Preference;->setSummary(Ljava/lang/CharSequence;)V

    invoke-virtual {v13, v4}, Landroidx/preference/Preference;->setOrder(I)V

    add-int/lit8 v4, v4, 0x1

    new-instance v14, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ValueClickListener;

    invoke-direct {v14, p0, v6, v9, v10}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ValueClickListener;-><init>(Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V

    invoke-virtual {v13, v14}, Landroidx/preference/Preference;->setOnPreferenceClickListener(Landroidx/preference/Preference$OnPreferenceClickListener;)V

    invoke-virtual {v0, v13}, Landroidx/preference/PreferenceGroup;->addPreference(Landroidx/preference/Preference;)Z

    goto :goto_1

    :cond_7
    return-void
.end method

.method public final showSearchDialog()V
    .locals 6

    invoke-virtual {p0}, Lcom/android/settings/core/InstrumentedPreferenceFragment;->getPrefContext()Landroid/content/Context;

    move-result-object v0

    new-instance v1, Landroid/widget/EditText;

    invoke-direct {v1, v0}, Landroid/widget/EditText;-><init>(Landroid/content/Context;)V

    const/4 v2, 0x1

    invoke-virtual {v1, v2}, Landroid/widget/EditText;->setSingleLine(Z)V

    iget-object v2, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->mSearchQuery:Ljava/lang/String;

    invoke-static {v2}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z

    move-result v3

    if-nez v3, :cond_0

    invoke-virtual {v1, v2}, Landroid/widget/EditText;->setText(Ljava/lang/CharSequence;)V

    :cond_0
    const/4 v2, 0x1

    invoke-virtual {v1, v2}, Landroid/widget/EditText;->setSelectAllOnFocus(Z)V

    new-instance v2, Landroid/app/AlertDialog$Builder;

    invoke-direct {v2, v0}, Landroid/app/AlertDialog$Builder;-><init>(Landroid/content/Context;)V

    const-string v0, "Search camera features"

    invoke-virtual {v2, v0}, Landroid/app/AlertDialog$Builder;->setTitle(Ljava/lang/CharSequence;)Landroid/app/AlertDialog$Builder;

    invoke-virtual {v2, v1}, Landroid/app/AlertDialog$Builder;->setView(Landroid/view/View;)Landroid/app/AlertDialog$Builder;

    new-instance v0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$SearchDialogClickListener;

    invoke-direct {v0, p0, v1}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$SearchDialogClickListener;-><init>(Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;Landroid/widget/EditText;)V

    const-string p0, "Apply"

    invoke-virtual {v2, p0, v0}, Landroid/app/AlertDialog$Builder;->setPositiveButton(Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)Landroid/app/AlertDialog$Builder;

    const-string p0, "Cancel"

    const/4 v0, 0x0

    invoke-virtual {v2, p0, v0}, Landroid/app/AlertDialog$Builder;->setNegativeButton(Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)Landroid/app/AlertDialog$Builder;

    invoke-virtual {v2}, Landroid/app/AlertDialog$Builder;->show()Landroid/app/AlertDialog;

    return-void
.end method

.method public final showToast(Ljava/lang/String;)V
    .locals 2

    invoke-virtual {p0}, Lcom/android/settings/core/InstrumentedPreferenceFragment;->getPrefContext()Landroid/content/Context;

    move-result-object p0

    const/4 v0, 0x0

    invoke-static {p0, p1, v0}, Landroid/widget/Toast;->makeText(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;

    move-result-object p0

    invoke-virtual {p0}, Landroid/widget/Toast;->show()V

    return-void
.end method

.method public final showValueDialog(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V
    .locals 6

    invoke-virtual {p0}, Lcom/android/settings/core/InstrumentedPreferenceFragment;->getPrefContext()Landroid/content/Context;

    move-result-object v0

    new-instance v1, Landroid/widget/EditText;

    invoke-direct {v1, v0}, Landroid/widget/EditText;-><init>(Landroid/content/Context;)V

    const/4 v2, 0x1

    invoke-virtual {v1, v2}, Landroid/widget/EditText;->setSingleLine(Z)V

    invoke-virtual {v1, p3}, Landroid/widget/EditText;->setText(Ljava/lang/CharSequence;)V

    invoke-virtual {v1, v2}, Landroid/widget/EditText;->setSelectAllOnFocus(Z)V

    new-instance p3, Landroid/app/AlertDialog$Builder;

    invoke-direct {p3, v0}, Landroid/app/AlertDialog$Builder;-><init>(Landroid/content/Context;)V

    invoke-static {p1, p2}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->buildTitle(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    invoke-virtual {p3, v0}, Landroid/app/AlertDialog$Builder;->setTitle(Ljava/lang/CharSequence;)Landroid/app/AlertDialog$Builder;

    invoke-virtual {p3, v1}, Landroid/app/AlertDialog$Builder;->setView(Landroid/view/View;)Landroid/app/AlertDialog$Builder;

    new-instance v0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ValueDialogClickListener;

    invoke-direct {v0, p0, p1, p2, v1}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ValueDialogClickListener;-><init>(Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;Ljava/lang/String;Ljava/lang/String;Landroid/widget/EditText;)V

    const-string p0, "Save"

    invoke-virtual {p3, p0, v0}, Landroid/app/AlertDialog$Builder;->setPositiveButton(Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)Landroid/app/AlertDialog$Builder;

    const-string p0, "Cancel"

    const/4 p1, 0x0

    invoke-virtual {p3, p0, p1}, Landroid/app/AlertDialog$Builder;->setNegativeButton(Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)Landroid/app/AlertDialog$Builder;

    invoke-virtual {p3}, Landroid/app/AlertDialog$Builder;->show()Landroid/app/AlertDialog;

    return-void
.end method
