.class public Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;
.super Lcom/android/settings/SettingsPreferenceFragment;
.source "ScpmAllowlistFragment.java"

# interfaces
.implements Landroidx/preference/Preference$OnPreferenceClickListener;


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;
    }
.end annotation


# static fields
.field private static final KEY_ENABLE_ALL:Ljava/lang/String; = "unica_scpm_allowlist_enable_all"

.field private static final KEY_PACKAGES:Ljava/lang/String; = "unica_scpm_allowlist_packages"

.field private static final KEY_REGION_BYPASS:Ljava/lang/String; = "unica_scpm_region_bypass"

.field private static final PROP_APPLY:Ljava/lang/String; = "persist.sys.unica.scpm_allowlist.apply"

.field private static final PROP_REGION_BYPASS:Ljava/lang/String; = "persist.sys.unica.scpm_region_bypass"

.field public static final SEARCH_INDEX_DATA_PROVIDER:Lcom/android/settings/search/BaseSearchIndexProvider;


# instance fields
.field private mContext:Landroid/content/Context;

.field private mRegionBypassPreference:Landroidx/preference/SecSwitchPreference;

.field private mSelectedPreference:Landroidx/preference/Preference;


# direct methods
.method static constructor <clinit>()V
    .registers 3

    .line 27
    new-instance v0, Lcom/android/settings/search/BaseSearchIndexProvider;

    const-string v1, "xml"

    const-string v2, "unica_scpm_allowlist_settings"

    invoke-static {v1, v2}, Lio/mesalabs/unica/utils/Utils;->getResourceId(Ljava/lang/String;Ljava/lang/String;)I

    move-result v1

    invoke-direct {v0, v1}, Lcom/android/settings/search/BaseSearchIndexProvider;-><init>(I)V

    sput-object v0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->SEARCH_INDEX_DATA_PROVIDER:Lcom/android/settings/search/BaseSearchIndexProvider;

    return-void
.end method

.method public constructor <init>()V
    .registers 1

    .line 26
    invoke-direct {p0}, Lcom/android/settings/SettingsPreferenceFragment;-><init>()V

    return-void
.end method

.method static synthetic access$000(Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;)Landroid/content/Context;
    .registers 1

    .line 26
    iget-object p0, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mContext:Landroid/content/Context;

    return-object p0
.end method

.method static synthetic access$100(Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;Ljava/lang/Iterable;)V
    .registers 2

    .line 26
    invoke-direct {p0, p1}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->savePackages(Ljava/lang/Iterable;)V

    return-void
.end method

.method static synthetic access$200(Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;)V
    .registers 1

    .line 26
    invoke-direct {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->writeControlFiles()V

    return-void
.end method

.method static synthetic access$300(Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;)V
    .registers 1

    .line 26
    invoke-direct {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->refreshState()V

    return-void
.end method

.method private applyPolicyFiles()V
    .registers 3

    .line 359
    invoke-direct {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getEffectivePackages()Ljava/util/List;

    move-result-object v0

    .line 360
    invoke-interface {v0}, Ljava/util/List;->isEmpty()Z

    move-result v1

    if-eqz v1, :cond_10

    .line 361
    const-string v0, "unica_scpm_allowlist_no_selection"

    invoke-direct {p0, v0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->toast(Ljava/lang/String;)V

    .line 362
    return-void

    .line 364
    :cond_10
    invoke-direct {p0, v0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->savePackages(Ljava/lang/Iterable;)V

    .line 365
    invoke-direct {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->isRegionBypassEnabled()Z

    move-result v0

    invoke-static {v0}, Ljava/lang/Boolean;->toString(Z)Ljava/lang/String;

    move-result-object v0

    const-string v1, "persist.sys.unica.scpm_region_bypass"

    invoke-static {v1, v0}, Landroid/os/SemSystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V

    .line 366
    const-string v0, "persist.sys.unica.scpm_allowlist.apply"

    const-string v1, "1"

    invoke-static {v0, v1}, Landroid/os/SemSystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V

    .line 367
    const-string v0, "unica_scpm_allowlist_applied"

    invoke-direct {p0, v0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->toast(Ljava/lang/String;)V

    .line 368
    invoke-direct {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->refreshState()V

    .line 369
    return-void
.end method

.method private buildPreference(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Landroidx/preference/Preference;
    .registers 6
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x0,
            0x0,
            0x0
        }
        names = {
            "key",
            "titleName",
            "summaryName"
        }
    .end annotation

    .line 120
    new-instance v0, Landroidx/preference/Preference;

    iget-object v1, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mContext:Landroid/content/Context;

    invoke-direct {v0, v1}, Landroidx/preference/Preference;-><init>(Landroid/content/Context;)V

    .line 121
    invoke-virtual {v0, p1}, Landroidx/preference/Preference;->setKey(Ljava/lang/String;)V

    .line 122
    invoke-direct {p0, p2}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getStringId(Ljava/lang/String;)I

    move-result p1

    invoke-virtual {v0, p1}, Landroidx/preference/Preference;->setTitle(I)V

    .line 123
    invoke-direct {p0, p3}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getStringId(Ljava/lang/String;)I

    move-result p1

    invoke-virtual {v0, p1}, Landroidx/preference/Preference;->setSummary(I)V

    .line 124
    const/4 p1, 0x0

    invoke-virtual {v0, p1}, Landroidx/preference/Preference;->setPersistent(Z)V

    .line 125
    return-object v0
.end method

.method private ensureDefaults()V
    .registers 4

    .line 133
    iget-object v0, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mContext:Landroid/content/Context;

    invoke-virtual {v0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v0

    const-string v1, "unica_scpm_region_bypass"

    invoke-static {v0, v1}, Landroid/provider/Settings$System;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    if-nez v0, :cond_1f

    .line 134
    iget-object v0, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mContext:Landroid/content/Context;

    invoke-virtual {v0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v0

    const/4 v2, 0x1

    invoke-static {v0, v1, v2}, Landroid/provider/Settings$System;->putInt(Landroid/content/ContentResolver;Ljava/lang/String;I)Z

    .line 135
    const-string v0, "persist.sys.unica.scpm_region_bypass"

    const-string v1, "true"

    invoke-static {v0, v1}, Landroid/os/SemSystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V

    .line 137
    :cond_1f
    return-void
.end method

.method private getCandidateApps()Ljava/util/List;
    .registers 7
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()",
            "Ljava/util/List<",
            "Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;",
            ">;"
        }
    .end annotation

    .line 246
    iget-object v0, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mContext:Landroid/content/Context;

    invoke-virtual {v0}, Landroid/content/Context;->getPackageManager()Landroid/content/pm/PackageManager;

    move-result-object v0

    .line 247
    const/4 v1, 0x0

    invoke-virtual {v0, v1}, Landroid/content/pm/PackageManager;->getInstalledApplications(I)Ljava/util/List;

    move-result-object v1

    .line 248
    new-instance v2, Ljava/util/ArrayList;

    invoke-direct {v2}, Ljava/util/ArrayList;-><init>()V

    .line 249
    invoke-interface {v1}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v1

    :cond_14
    :goto_14
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z

    move-result v3

    if-eqz v3, :cond_56

    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v3

    check-cast v3, Landroid/content/pm/ApplicationInfo;

    .line 250
    if-eqz v3, :cond_14

    iget-object v4, v3, Landroid/content/pm/ApplicationInfo;->packageName:Ljava/lang/String;

    if-nez v4, :cond_27

    .line 251
    goto :goto_14

    .line 253
    :cond_27
    iget-object v4, v3, Landroid/content/pm/ApplicationInfo;->packageName:Ljava/lang/String;

    invoke-virtual {v0, v4}, Landroid/content/pm/PackageManager;->getLaunchIntentForPackage(Ljava/lang/String;)Landroid/content/Intent;

    move-result-object v4

    if-nez v4, :cond_38

    iget-object v4, v3, Landroid/content/pm/ApplicationInfo;->packageName:Ljava/lang/String;

    invoke-direct {p0, v4}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->isKnownMediaPackage(Ljava/lang/String;)Z

    move-result v4

    if-nez v4, :cond_38

    .line 254
    goto :goto_14

    .line 256
    :cond_38
    invoke-virtual {v0, v3}, Landroid/content/pm/PackageManager;->getApplicationLabel(Landroid/content/pm/ApplicationInfo;)Ljava/lang/CharSequence;

    move-result-object v4

    .line 257
    if-nez v4, :cond_41

    iget-object v4, v3, Landroid/content/pm/ApplicationInfo;->packageName:Ljava/lang/String;

    goto :goto_45

    :cond_41
    invoke-interface {v4}, Ljava/lang/CharSequence;->toString()Ljava/lang/String;

    move-result-object v4

    .line 258
    :goto_45
    invoke-direct {p0, v3, v4}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->isCandidate(Landroid/content/pm/ApplicationInfo;Ljava/lang/String;)Z

    move-result v5

    if-eqz v5, :cond_55

    .line 259
    new-instance v5, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;

    iget-object v3, v3, Landroid/content/pm/ApplicationInfo;->packageName:Ljava/lang/String;

    invoke-direct {v5, v3, v4}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;-><init>(Ljava/lang/String;Ljava/lang/String;)V

    invoke-virtual {v2, v5}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    .line 261
    :cond_55
    goto :goto_14

    .line 262
    :cond_56
    new-instance v0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$5;

    invoke-direct {v0, p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$5;-><init>(Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;)V

    invoke-static {v2, v0}, Ljava/util/Collections;->sort(Ljava/util/List;Ljava/util/Comparator;)V

    .line 267
    return-object v2
.end method

.method private getEffectivePackages()Ljava/util/List;
    .registers 3
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()",
            "Ljava/util/List<",
            "Ljava/lang/String;",
            ">;"
        }
    .end annotation

    .line 339
    invoke-direct {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->isEnableAll()Z

    move-result v0

    if-eqz v0, :cond_f

    .line 340
    invoke-direct {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getCandidateApps()Ljava/util/List;

    move-result-object v0

    invoke-direct {p0, v0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->packagesFromEntries(Ljava/util/List;)Ljava/util/List;

    move-result-object v0

    return-object v0

    .line 342
    :cond_f
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getStoredPackages()Ljava/util/Set;

    move-result-object v1

    invoke-direct {v0, v1}, Ljava/util/ArrayList;-><init>(Ljava/util/Collection;)V

    .line 343
    invoke-static {v0}, Ljava/util/Collections;->sort(Ljava/util/List;)V

    .line 344
    return-object v0
.end method

.method private getInstalledPackageApps()Ljava/util/List;
    .registers 7
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()",
            "Ljava/util/List<",
            "Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;",
            ">;"
        }
    .end annotation

    .line 271
    iget-object v0, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mContext:Landroid/content/Context;

    invoke-virtual {v0}, Landroid/content/Context;->getPackageManager()Landroid/content/pm/PackageManager;

    move-result-object v0

    .line 272
    const/4 v1, 0x0

    invoke-virtual {v0, v1}, Landroid/content/pm/PackageManager;->getInstalledApplications(I)Ljava/util/List;

    move-result-object v1

    .line 273
    new-instance v2, Ljava/util/ArrayList;

    invoke-direct {v2}, Ljava/util/ArrayList;-><init>()V

    .line 274
    invoke-interface {v1}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v1

    :cond_14
    :goto_14
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z

    move-result v3

    if-eqz v3, :cond_3f

    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v3

    check-cast v3, Landroid/content/pm/ApplicationInfo;

    .line 275
    if-eqz v3, :cond_14

    iget-object v4, v3, Landroid/content/pm/ApplicationInfo;->packageName:Ljava/lang/String;

    if-nez v4, :cond_27

    .line 276
    goto :goto_14

    .line 278
    :cond_27
    invoke-virtual {v0, v3}, Landroid/content/pm/PackageManager;->getApplicationLabel(Landroid/content/pm/ApplicationInfo;)Ljava/lang/CharSequence;

    move-result-object v4

    .line 279
    if-nez v4, :cond_30

    iget-object v4, v3, Landroid/content/pm/ApplicationInfo;->packageName:Ljava/lang/String;

    goto :goto_34

    :cond_30
    invoke-interface {v4}, Ljava/lang/CharSequence;->toString()Ljava/lang/String;

    move-result-object v4

    .line 280
    :goto_34
    new-instance v5, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;

    iget-object v3, v3, Landroid/content/pm/ApplicationInfo;->packageName:Ljava/lang/String;

    invoke-direct {v5, v3, v4}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;-><init>(Ljava/lang/String;Ljava/lang/String;)V

    invoke-virtual {v2, v5}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    .line 281
    goto :goto_14

    .line 282
    :cond_3f
    new-instance v0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$6;

    invoke-direct {v0, p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$6;-><init>(Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;)V

    invoke-static {v2, v0}, Ljava/util/Collections;->sort(Ljava/util/List;Ljava/util/Comparator;)V

    .line 291
    return-object v2
.end method

.method private getStoredPackages()Ljava/util/Set;
    .registers 6
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()",
            "Ljava/util/Set<",
            "Ljava/lang/String;",
            ">;"
        }
    .end annotation

    .line 323
    new-instance v0, Ljava/util/LinkedHashSet;

    invoke-direct {v0}, Ljava/util/LinkedHashSet;-><init>()V

    .line 324
    iget-object v1, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mContext:Landroid/content/Context;

    invoke-virtual {v1}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v1

    const-string v2, "unica_scpm_allowlist_packages"

    invoke-static {v1, v2}, Landroid/provider/Settings$System;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    .line 325
    if-eqz v1, :cond_37

    invoke-virtual {v1}, Ljava/lang/String;->length()I

    move-result v2

    if-nez v2, :cond_1a

    goto :goto_37

    .line 328
    :cond_1a
    const-string v2, "\\n"

    invoke-virtual {v1, v2}, Ljava/lang/String;->split(Ljava/lang/String;)[Ljava/lang/String;

    move-result-object v1

    .line 329
    const/4 v2, 0x0

    :goto_21
    array-length v3, v1

    if-ge v2, v3, :cond_36

    .line 330
    aget-object v3, v1, v2

    invoke-virtual {v3}, Ljava/lang/String;->trim()Ljava/lang/String;

    move-result-object v3

    .line 331
    invoke-virtual {v3}, Ljava/lang/String;->length()I

    move-result v4

    if-lez v4, :cond_33

    .line 332
    invoke-virtual {v0, v3}, Ljava/util/LinkedHashSet;->add(Ljava/lang/Object;)Z

    .line 329
    :cond_33
    add-int/lit8 v2, v2, 0x1

    goto :goto_21

    .line 335
    :cond_36
    return-object v0

    .line 326
    :cond_37
    :goto_37
    return-object v0
.end method

.method private getStringId(Ljava/lang/String;)I
    .registers 3
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x0
        }
        names = {
            "name"
        }
    .end annotation

    .line 129
    const-string v0, "string"

    invoke-static {v0, p1}, Lio/mesalabs/unica/utils/Utils;->getResourceId(Ljava/lang/String;Ljava/lang/String;)I

    move-result p1

    return p1
.end method

.method private isCandidate(Landroid/content/pm/ApplicationInfo;Ljava/lang/String;)Z
    .registers 44
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x0,
            0x0
        }
        names = {
            "info",
            "label"
        }
    .end annotation

    .line 295
    move-object/from16 v0, p1

    iget v1, v0, Landroid/content/pm/ApplicationInfo;->category:I

    .line 296
    const/4 v2, 0x1

    if-eq v1, v2, :cond_91

    const/4 v3, 0x2

    if-eq v1, v3, :cond_91

    const/4 v3, 0x4

    if-ne v1, v3, :cond_f

    goto/16 :goto_91

    .line 299
    :cond_f
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    iget-object v0, v0, Landroid/content/pm/ApplicationInfo;->packageName:Ljava/lang/String;

    invoke-virtual {v1, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    const-string v1, " "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    move-object/from16 v1, p2

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-virtual {v0}, Ljava/lang/String;->toLowerCase()Ljava/lang/String;

    move-result-object v0

    .line 300
    const-string v3, "message"

    const-string v4, "messag"

    const-string v5, "chat"

    const-string v6, "talk"

    const-string v7, "whatsapp"

    const-string v8, "telegram"

    const-string v9, "signal"

    const-string v10, "viber"

    const-string v11, "discord"

    const-string v12, "teams"

    const-string v13, "line"

    const-string v14, "kakao"

    const-string v15, "wechat"

    const-string v16, "messenger"

    const-string v17, "music"

    const-string v18, "audio"

    const-string v19, "sound"

    const-string v20, "spotify"

    const-string v21, "podcast"

    const-string v22, "radio"

    const-string v23, "player"

    const-string v24, "video"

    const-string v25, "movie"

    const-string v26, "stream"

    const-string v27, "youtube"

    const-string v28, "netflix"

    const-string v29, "primevideo"

    const-string v30, "disney"

    const-string v31, "hulu"

    const-string v32, "media"

    const-string v33, "gallery"

    const-string v34, "photo"

    const-string v35, "camera"

    const-string v36, "reels"

    const-string v37, "tiktok"

    const-string v38, "instagram"

    const-string v39, "snapchat"

    const-string v40, "facebook"

    filled-new-array/range {v3 .. v40}, [Ljava/lang/String;

    move-result-object v1

    .line 301
    const/4 v3, 0x0

    move v4, v3

    :goto_80
    const/16 v5, 0x26

    if-ge v4, v5, :cond_90

    .line 302
    aget-object v5, v1, v4

    invoke-virtual {v0, v5}, Ljava/lang/String;->indexOf(Ljava/lang/String;)I

    move-result v5

    if-ltz v5, :cond_8d

    .line 303
    return v2

    .line 301
    :cond_8d
    add-int/lit8 v4, v4, 0x1

    goto :goto_80

    .line 306
    :cond_90
    return v3

    .line 297
    :cond_91
    :goto_91
    return v2
.end method

.method private isEnableAll()Z
    .registers 4

    .line 156
    iget-object v0, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mContext:Landroid/content/Context;

    invoke-virtual {v0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v0

    const-string v1, "unica_scpm_allowlist_enable_all"

    const/4 v2, 0x0

    invoke-static {v0, v1, v2}, Landroid/provider/Settings$System;->getInt(Landroid/content/ContentResolver;Ljava/lang/String;I)I

    move-result v0

    const/4 v1, 0x1

    if-ne v0, v1, :cond_11

    move v2, v1

    :cond_11
    return v2
.end method

.method private isKnownMediaPackage(Ljava/lang/String;)Z
    .registers 3
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x0
        }
        names = {
            "packageName"
        }
    .end annotation

    .line 310
    invoke-virtual {p1}, Ljava/lang/String;->toLowerCase()Ljava/lang/String;

    move-result-object p1

    .line 311
    const-string v0, "youtube"

    invoke-virtual {p1, v0}, Ljava/lang/String;->indexOf(Ljava/lang/String;)I

    move-result v0

    if-gez v0, :cond_27

    const-string v0, "netflix"

    invoke-virtual {p1, v0}, Ljava/lang/String;->indexOf(Ljava/lang/String;)I

    move-result v0

    if-gez v0, :cond_27

    const-string v0, "spotify"

    invoke-virtual {p1, v0}, Ljava/lang/String;->indexOf(Ljava/lang/String;)I

    move-result v0

    if-gez v0, :cond_27

    const-string v0, "tiktok"

    invoke-virtual {p1, v0}, Ljava/lang/String;->indexOf(Ljava/lang/String;)I

    move-result p1

    if-ltz p1, :cond_25

    goto :goto_27

    :cond_25
    const/4 p1, 0x0

    goto :goto_28

    :cond_27
    :goto_27
    const/4 p1, 0x1

    :goto_28
    return p1
.end method

.method private isRegionBypassEnabled()Z
    .registers 4

    .line 160
    iget-object v0, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mContext:Landroid/content/Context;

    invoke-virtual {v0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v0

    const-string v1, "unica_scpm_region_bypass"

    const/4 v2, 0x1

    invoke-static {v0, v1, v2}, Landroid/provider/Settings$System;->getInt(Landroid/content/ContentResolver;Ljava/lang/String;I)I

    move-result v0

    if-ne v0, v2, :cond_10

    goto :goto_11

    :cond_10
    const/4 v2, 0x0

    :goto_11
    return v2
.end method

.method private joinLines(Ljava/util/List;)Ljava/lang/String;
    .registers 5
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x0
        }
        names = {
            "packages"
        }
    .end annotation

    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/util/List<",
            "Ljava/lang/String;",
            ">;)",
            "Ljava/lang/String;"
        }
    .end annotation

    .line 376
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    .line 377
    const/4 v1, 0x0

    :goto_6
    invoke-interface {p1}, Ljava/util/List;->size()I

    move-result v2

    if-ge v1, v2, :cond_1d

    .line 378
    invoke-interface {p1, v1}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Ljava/lang/String;

    invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 379
    const/16 v2, 0xa

    invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;->append(C)Ljava/lang/StringBuilder;

    .line 377
    add-int/lit8 v1, v1, 0x1

    goto :goto_6

    .line 381
    :cond_1d
    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    return-object p1
.end method

.method private packagesFromEntries(Ljava/util/List;)Ljava/util/List;
    .registers 5
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x0
        }
        names = {
            "entries"
        }
    .end annotation

    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/util/List<",
            "Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;",
            ">;)",
            "Ljava/util/List<",
            "Ljava/lang/String;",
            ">;"
        }
    .end annotation

    .line 315
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    .line 316
    const/4 v1, 0x0

    :goto_6
    invoke-interface {p1}, Ljava/util/List;->size()I

    move-result v2

    if-ge v1, v2, :cond_1a

    .line 317
    invoke-interface {p1, v1}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;

    iget-object v2, v2, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;->packageName:Ljava/lang/String;

    invoke-virtual {v0, v2}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    .line 316
    add-int/lit8 v1, v1, 0x1

    goto :goto_6

    .line 319
    :cond_1a
    return-object v0
.end method

.method private refreshState()V
    .registers 4

    .line 140
    iget-object v0, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mRegionBypassPreference:Landroidx/preference/SecSwitchPreference;

    if-eqz v0, :cond_b

    .line 141
    invoke-direct {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->isRegionBypassEnabled()Z

    move-result v1

    invoke-virtual {v0, v1}, Landroidx/preference/SecSwitchPreference;->setChecked(Z)V

    .line 143
    :cond_b
    iget-object v0, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mSelectedPreference:Landroidx/preference/Preference;

    if-eqz v0, :cond_62

    .line 144
    invoke-direct {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getEffectivePackages()Ljava/util/List;

    move-result-object v0

    .line 145
    invoke-interface {v0}, Ljava/util/List;->isEmpty()Z

    move-result v1

    if-eqz v1, :cond_25

    .line 146
    iget-object v0, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mSelectedPreference:Landroidx/preference/Preference;

    const-string v1, "unica_scpm_allowlist_selected_empty"

    invoke-direct {p0, v1}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getStringId(Ljava/lang/String;)I

    move-result v1

    invoke-virtual {v0, v1}, Landroidx/preference/Preference;->setSummary(I)V

    goto :goto_62

    .line 147
    :cond_25
    invoke-direct {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->isEnableAll()Z

    move-result v1

    if-eqz v1, :cond_47

    .line 148
    iget-object v1, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mSelectedPreference:Landroidx/preference/Preference;

    const-string v2, "unica_scpm_allowlist_selected_all"

    invoke-direct {p0, v2}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getStringId(Ljava/lang/String;)I

    move-result v2

    invoke-interface {v0}, Ljava/util/List;->size()I

    move-result v0

    invoke-static {v0}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v0

    filled-new-array {v0}, [Ljava/lang/Object;

    move-result-object v0

    invoke-virtual {p0, v2, v0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getString(I[Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v0

    invoke-virtual {v1, v0}, Landroidx/preference/Preference;->setSummary(Ljava/lang/CharSequence;)V

    goto :goto_62

    .line 150
    :cond_47
    iget-object v1, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mSelectedPreference:Landroidx/preference/Preference;

    const-string v2, "unica_scpm_allowlist_selected_count"

    invoke-direct {p0, v2}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getStringId(Ljava/lang/String;)I

    move-result v2

    invoke-interface {v0}, Ljava/util/List;->size()I

    move-result v0

    invoke-static {v0}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v0

    filled-new-array {v0}, [Ljava/lang/Object;

    move-result-object v0

    invoke-virtual {p0, v2, v0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getString(I[Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v0

    invoke-virtual {v1, v0}, Landroidx/preference/Preference;->setSummary(Ljava/lang/CharSequence;)V

    .line 153
    :cond_62
    :goto_62
    return-void
.end method

.method private savePackages(Ljava/lang/Iterable;)V
    .registers 5
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x0
        }
        names = {
            "packages"
        }
    .end annotation

    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/lang/Iterable<",
            "Ljava/lang/String;",
            ">;)V"
        }
    .end annotation

    .line 348
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    .line 349
    invoke-interface {p1}, Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;

    move-result-object p1

    :goto_9
    invoke-interface {p1}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_29

    invoke-interface {p1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Ljava/lang/String;

    .line 350
    if-eqz v1, :cond_28

    invoke-virtual {v1}, Ljava/lang/String;->trim()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v2}, Ljava/lang/String;->length()I

    move-result v2

    if-lez v2, :cond_28

    .line 351
    invoke-virtual {v1}, Ljava/lang/String;->trim()Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    .line 353
    :cond_28
    goto :goto_9

    .line 354
    :cond_29
    invoke-static {v0}, Ljava/util/Collections;->sort(Ljava/util/List;)V

    .line 355
    iget-object p1, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mContext:Landroid/content/Context;

    invoke-virtual {p1}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object p1

    const-string v1, "unica_scpm_allowlist_packages"

    invoke-direct {p0, v0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->joinLines(Ljava/util/List;)Ljava/lang/String;

    move-result-object v0

    invoke-static {p1, v1, v0}, Landroid/provider/Settings$System;->putString(Landroid/content/ContentResolver;Ljava/lang/String;Ljava/lang/String;)Z

    .line 356
    return-void
.end method

.method private showAppPicker()V
    .registers 10

    .line 164
    invoke-direct {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getCandidateApps()Ljava/util/List;

    move-result-object v0

    .line 165
    invoke-interface {v0}, Ljava/util/List;->isEmpty()Z

    move-result v1

    if-eqz v1, :cond_10

    .line 166
    const-string v0, "unica_scpm_allowlist_no_apps"

    invoke-direct {p0, v0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->toast(Ljava/lang/String;)V

    .line 167
    return-void

    .line 170
    :cond_10
    invoke-direct {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getStoredPackages()Ljava/util/Set;

    move-result-object v1

    .line 171
    invoke-interface {v0}, Ljava/util/List;->size()I

    move-result v2

    new-array v2, v2, [Ljava/lang/CharSequence;

    .line 172
    invoke-interface {v0}, Ljava/util/List;->size()I

    move-result v3

    new-array v3, v3, [Z

    .line 173
    const/4 v4, 0x0

    move v5, v4

    :goto_22
    invoke-interface {v0}, Ljava/util/List;->size()I

    move-result v6

    if-ge v5, v6, :cond_62

    .line 174
    invoke-interface {v0, v5}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v6

    check-cast v6, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;

    .line 175
    new-instance v7, Ljava/lang/StringBuilder;

    invoke-direct {v7}, Ljava/lang/StringBuilder;-><init>()V

    iget-object v8, v6, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;->label:Ljava/lang/String;

    invoke-virtual {v7, v8}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v7

    const-string v8, "\n"

    invoke-virtual {v7, v8}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v7

    iget-object v8, v6, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;->packageName:Ljava/lang/String;

    invoke-virtual {v7, v8}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v7

    invoke-virtual {v7}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v7

    aput-object v7, v2, v5

    .line 176
    iget-object v6, v6, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;->packageName:Ljava/lang/String;

    invoke-interface {v1, v6}, Ljava/util/Set;->contains(Ljava/lang/Object;)Z

    move-result v6

    if-nez v6, :cond_5c

    invoke-direct {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->isEnableAll()Z

    move-result v6

    if-eqz v6, :cond_5a

    goto :goto_5c

    :cond_5a
    move v6, v4

    goto :goto_5d

    :cond_5c
    :goto_5c
    const/4 v6, 0x1

    :goto_5d
    aput-boolean v6, v3, v5

    .line 173
    add-int/lit8 v5, v5, 0x1

    goto :goto_22

    .line 179
    :cond_62
    new-instance v1, Landroid/app/AlertDialog$Builder;

    iget-object v4, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mContext:Landroid/content/Context;

    invoke-direct {v1, v4}, Landroid/app/AlertDialog$Builder;-><init>(Landroid/content/Context;)V

    .line 180
    const-string v4, "unica_scpm_allowlist_select_title"

    invoke-direct {p0, v4}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getStringId(Ljava/lang/String;)I

    move-result v4

    invoke-virtual {v1, v4}, Landroid/app/AlertDialog$Builder;->setTitle(I)Landroid/app/AlertDialog$Builder;

    move-result-object v1

    new-instance v4, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$2;

    invoke-direct {v4, p0, v3}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$2;-><init>(Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;[Z)V

    .line 181
    invoke-virtual {v1, v2, v3, v4}, Landroid/app/AlertDialog$Builder;->setMultiChoiceItems([Ljava/lang/CharSequence;[ZLandroid/content/DialogInterface$OnMultiChoiceClickListener;)Landroid/app/AlertDialog$Builder;

    move-result-object v1

    .line 186
    const-string v2, "unica_scpm_allowlist_save"

    invoke-direct {p0, v2}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getStringId(Ljava/lang/String;)I

    move-result v2

    new-instance v4, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$1;

    invoke-direct {v4, p0, v0, v3}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$1;-><init>(Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;Ljava/util/List;[Z)V

    invoke-virtual {v1, v2, v4}, Landroid/app/AlertDialog$Builder;->setPositiveButton(ILandroid/content/DialogInterface$OnClickListener;)Landroid/app/AlertDialog$Builder;

    move-result-object v0

    .line 200
    const-string v1, "unica_dialog_reboot_negative"

    invoke-direct {p0, v1}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getStringId(Ljava/lang/String;)I

    move-result v1

    const/4 v2, 0x0

    invoke-virtual {v0, v1, v2}, Landroid/app/AlertDialog$Builder;->setNegativeButton(ILandroid/content/DialogInterface$OnClickListener;)Landroid/app/AlertDialog$Builder;

    move-result-object v0

    .line 201
    invoke-virtual {v0}, Landroid/app/AlertDialog$Builder;->show()Landroid/app/AlertDialog;

    .line 202
    return-void
.end method

.method private showManualPackageDialog()V
    .registers 9

    .line 205
    invoke-direct {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getInstalledPackageApps()Ljava/util/List;

    move-result-object v0

    .line 206
    invoke-interface {v0}, Ljava/util/List;->isEmpty()Z

    move-result v1

    if-eqz v1, :cond_10

    .line 207
    const-string v0, "unica_scpm_allowlist_no_apps"

    invoke-direct {p0, v0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->toast(Ljava/lang/String;)V

    .line 208
    return-void

    .line 211
    :cond_10
    new-instance v1, Ljava/util/LinkedHashSet;

    invoke-direct {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getEffectivePackages()Ljava/util/List;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/util/LinkedHashSet;-><init>(Ljava/util/Collection;)V

    .line 212
    invoke-interface {v0}, Ljava/util/List;->size()I

    move-result v2

    new-array v2, v2, [Ljava/lang/CharSequence;

    .line 213
    invoke-interface {v0}, Ljava/util/List;->size()I

    move-result v3

    new-array v3, v3, [Z

    .line 214
    const/4 v4, 0x0

    :goto_26
    invoke-interface {v0}, Ljava/util/List;->size()I

    move-result v5

    if-ge v4, v5, :cond_5a

    .line 215
    invoke-interface {v0, v4}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v5

    check-cast v5, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;

    .line 216
    new-instance v6, Ljava/lang/StringBuilder;

    invoke-direct {v6}, Ljava/lang/StringBuilder;-><init>()V

    iget-object v7, v5, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;->label:Ljava/lang/String;

    invoke-virtual {v6, v7}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v6

    const-string v7, "\n"

    invoke-virtual {v6, v7}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v6

    iget-object v7, v5, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;->packageName:Ljava/lang/String;

    invoke-virtual {v6, v7}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v6

    invoke-virtual {v6}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v6

    aput-object v6, v2, v4

    .line 217
    iget-object v5, v5, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;->packageName:Ljava/lang/String;

    invoke-interface {v1, v5}, Ljava/util/Set;->contains(Ljava/lang/Object;)Z

    move-result v5

    aput-boolean v5, v3, v4

    .line 214
    add-int/lit8 v4, v4, 0x1

    goto :goto_26

    .line 220
    :cond_5a
    new-instance v1, Landroid/app/AlertDialog$Builder;

    iget-object v4, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mContext:Landroid/content/Context;

    invoke-direct {v1, v4}, Landroid/app/AlertDialog$Builder;-><init>(Landroid/content/Context;)V

    .line 221
    const-string v4, "unica_scpm_allowlist_manual_title"

    invoke-direct {p0, v4}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getStringId(Ljava/lang/String;)I

    move-result v4

    invoke-virtual {v1, v4}, Landroid/app/AlertDialog$Builder;->setTitle(I)Landroid/app/AlertDialog$Builder;

    move-result-object v1

    new-instance v4, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$4;

    invoke-direct {v4, p0, v3}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$4;-><init>(Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;[Z)V

    .line 222
    invoke-virtual {v1, v2, v3, v4}, Landroid/app/AlertDialog$Builder;->setMultiChoiceItems([Ljava/lang/CharSequence;[ZLandroid/content/DialogInterface$OnMultiChoiceClickListener;)Landroid/app/AlertDialog$Builder;

    move-result-object v1

    .line 227
    const-string v2, "unica_scpm_allowlist_save"

    invoke-direct {p0, v2}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getStringId(Ljava/lang/String;)I

    move-result v2

    new-instance v4, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$3;

    invoke-direct {v4, p0, v0, v3}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$3;-><init>(Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;Ljava/util/List;[Z)V

    invoke-virtual {v1, v2, v4}, Landroid/app/AlertDialog$Builder;->setPositiveButton(ILandroid/content/DialogInterface$OnClickListener;)Landroid/app/AlertDialog$Builder;

    move-result-object v0

    .line 241
    const-string v1, "unica_dialog_reboot_negative"

    invoke-direct {p0, v1}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getStringId(Ljava/lang/String;)I

    move-result v1

    const/4 v2, 0x0

    invoke-virtual {v0, v1, v2}, Landroid/app/AlertDialog$Builder;->setNegativeButton(ILandroid/content/DialogInterface$OnClickListener;)Landroid/app/AlertDialog$Builder;

    move-result-object v0

    .line 242
    invoke-virtual {v0}, Landroid/app/AlertDialog$Builder;->show()Landroid/app/AlertDialog;

    .line 243
    return-void
.end method

.method private toast(Ljava/lang/String;)V
    .registers 4
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x0
        }
        names = {
            "stringName"
        }
    .end annotation

    .line 385
    iget-object v0, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mContext:Landroid/content/Context;

    invoke-direct {p0, p1}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getStringId(Ljava/lang/String;)I

    move-result p1

    const/4 v1, 0x0

    invoke-static {v0, p1, v1}, Landroid/widget/Toast;->makeText(Landroid/content/Context;II)Landroid/widget/Toast;

    move-result-object p1

    invoke-virtual {p1}, Landroid/widget/Toast;->show()V

    .line 386
    return-void
.end method

.method private writeControlFiles()V
    .registers 3

    .line 372
    invoke-direct {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->isRegionBypassEnabled()Z

    move-result v0

    invoke-static {v0}, Ljava/lang/Boolean;->toString(Z)Ljava/lang/String;

    move-result-object v0

    const-string v1, "persist.sys.unica.scpm_region_bypass"

    invoke-static {v1, v0}, Landroid/os/SemSystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V

    .line 373
    return-void
.end method


# virtual methods
.method public getMetricsCategory()I
    .registers 2

    .line 40
    const/16 v0, 0x2e8

    return v0
.end method

.method public onCreate(Landroid/os/Bundle;)V
    .registers 5
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x0
        }
        names = {
            "bundle"
        }
    .end annotation

    .line 44
    invoke-super {p0, p1}, Lcom/android/settings/SettingsPreferenceFragment;->onCreate(Landroid/os/Bundle;)V

    .line 45
    invoke-virtual {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getContext()Landroid/content/Context;

    move-result-object p1

    iput-object p1, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mContext:Landroid/content/Context;

    .line 46
    invoke-direct {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->ensureDefaults()V

    .line 48
    invoke-virtual {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getPreferenceManager()Landroidx/preference/PreferenceManager;

    move-result-object p1

    iget-object v0, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mContext:Landroid/content/Context;

    invoke-virtual {p1, v0}, Landroidx/preference/PreferenceManager;->createPreferenceScreen(Landroid/content/Context;)Landroidx/preference/PreferenceScreen;

    move-result-object p1

    .line 49
    invoke-virtual {p0, p1}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->setPreferenceScreen(Landroidx/preference/PreferenceScreen;)V

    .line 51
    const-string v0, "unica_scpm_allowlist_select_title"

    const-string v1, "unica_scpm_allowlist_select_summary"

    const-string v2, "unica_scpm_allowlist_select"

    invoke-direct {p0, v2, v0, v1}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->buildPreference(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Landroidx/preference/Preference;

    move-result-object v0

    .line 52
    invoke-virtual {v0, p0}, Landroidx/preference/Preference;->setOnPreferenceClickListener(Landroidx/preference/Preference$OnPreferenceClickListener;)V

    .line 53
    invoke-virtual {p1, v0}, Landroidx/preference/PreferenceScreen;->addPreference(Landroidx/preference/Preference;)Z

    .line 55
    const-string v0, "unica_scpm_allowlist_manual_title"

    const-string v1, "unica_scpm_allowlist_manual_summary"

    const-string v2, "unica_scpm_allowlist_manual"

    invoke-direct {p0, v2, v0, v1}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->buildPreference(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Landroidx/preference/Preference;

    move-result-object v0

    .line 56
    invoke-virtual {v0, p0}, Landroidx/preference/Preference;->setOnPreferenceClickListener(Landroidx/preference/Preference$OnPreferenceClickListener;)V

    .line 57
    invoke-virtual {p1, v0}, Landroidx/preference/PreferenceScreen;->addPreference(Landroidx/preference/Preference;)Z

    .line 59
    const-string v0, "unica_scpm_allowlist_enable_all_title"

    const-string v1, "unica_scpm_allowlist_enable_all_summary"

    const-string v2, "unica_scpm_allowlist_enable_all_button"

    invoke-direct {p0, v2, v0, v1}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->buildPreference(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Landroidx/preference/Preference;

    move-result-object v0

    .line 60
    invoke-virtual {v0, p0}, Landroidx/preference/Preference;->setOnPreferenceClickListener(Landroidx/preference/Preference$OnPreferenceClickListener;)V

    .line 61
    invoke-virtual {p1, v0}, Landroidx/preference/PreferenceScreen;->addPreference(Landroidx/preference/Preference;)Z

    .line 63
    new-instance v0, Landroidx/preference/SecSwitchPreference;

    iget-object v1, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mContext:Landroid/content/Context;

    invoke-direct {v0, v1}, Landroidx/preference/SecSwitchPreference;-><init>(Landroid/content/Context;)V

    iput-object v0, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mRegionBypassPreference:Landroidx/preference/SecSwitchPreference;

    .line 64
    const-string v1, "unica_scpm_region_bypass_switch"

    invoke-virtual {v0, v1}, Landroidx/preference/SecSwitchPreference;->setKey(Ljava/lang/String;)V

    .line 65
    iget-object v0, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mRegionBypassPreference:Landroidx/preference/SecSwitchPreference;

    const-string v1, "unica_scpm_region_bypass_title"

    invoke-direct {p0, v1}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getStringId(Ljava/lang/String;)I

    move-result v1

    invoke-virtual {v0, v1}, Landroidx/preference/SecSwitchPreference;->setTitle(I)V

    .line 66
    iget-object v0, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mRegionBypassPreference:Landroidx/preference/SecSwitchPreference;

    const-string v1, "unica_scpm_region_bypass_summary"

    invoke-direct {p0, v1}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getStringId(Ljava/lang/String;)I

    move-result v1

    invoke-virtual {v0, v1}, Landroidx/preference/SecSwitchPreference;->setSummary(I)V

    .line 67
    iget-object v0, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mRegionBypassPreference:Landroidx/preference/SecSwitchPreference;

    const/4 v1, 0x0

    invoke-virtual {v0, v1}, Landroidx/preference/SecSwitchPreference;->setPersistent(Z)V

    .line 68
    iget-object v0, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mRegionBypassPreference:Landroidx/preference/SecSwitchPreference;

    invoke-virtual {v0, p0}, Landroidx/preference/SecSwitchPreference;->setOnPreferenceClickListener(Landroidx/preference/Preference$OnPreferenceClickListener;)V

    .line 69
    iget-object v0, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mRegionBypassPreference:Landroidx/preference/SecSwitchPreference;

    invoke-virtual {p1, v0}, Landroidx/preference/PreferenceScreen;->addPreference(Landroidx/preference/Preference;)Z

    .line 71
    const-string v0, "unica_scpm_allowlist_selected_title"

    const-string v1, "unica_scpm_allowlist_selected_empty"

    const-string v2, "unica_scpm_allowlist_selected"

    invoke-direct {p0, v2, v0, v1}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->buildPreference(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Landroidx/preference/Preference;

    move-result-object v0

    iput-object v0, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mSelectedPreference:Landroidx/preference/Preference;

    .line 72
    invoke-virtual {p1, v0}, Landroidx/preference/PreferenceScreen;->addPreference(Landroidx/preference/Preference;)Z

    .line 74
    const-string v0, "unica_scpm_allowlist_apply_title"

    const-string v1, "unica_scpm_allowlist_apply_summary"

    const-string v2, "unica_scpm_allowlist_apply"

    invoke-direct {p0, v2, v0, v1}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->buildPreference(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Landroidx/preference/Preference;

    move-result-object v0

    .line 75
    invoke-virtual {v0, p0}, Landroidx/preference/Preference;->setOnPreferenceClickListener(Landroidx/preference/Preference$OnPreferenceClickListener;)V

    .line 76
    invoke-virtual {p1, v0}, Landroidx/preference/PreferenceScreen;->addPreference(Landroidx/preference/Preference;)Z

    .line 78
    invoke-direct {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->refreshState()V

    .line 79
    return-void
.end method

.method public onPreferenceClick(Landroidx/preference/Preference;)Z
    .registers 5
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x0
        }
        names = {
            "preference"
        }
    .end annotation

    .line 87
    invoke-virtual {p1}, Landroidx/preference/Preference;->getKey()Ljava/lang/String;

    move-result-object p1

    .line 88
    const-string v0, "unica_scpm_allowlist_select"

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    const/4 v1, 0x1

    if-eqz v0, :cond_11

    .line 89
    invoke-direct {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->showAppPicker()V

    .line 90
    return v1

    .line 92
    :cond_11
    const-string v0, "unica_scpm_allowlist_manual"

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_1d

    .line 93
    invoke-direct {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->showManualPackageDialog()V

    .line 94
    return v1

    .line 96
    :cond_1d
    const-string v0, "unica_scpm_allowlist_enable_all_button"

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_47

    .line 97
    iget-object p1, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mContext:Landroid/content/Context;

    invoke-virtual {p1}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object p1

    const-string v0, "unica_scpm_allowlist_enable_all"

    invoke-static {p1, v0, v1}, Landroid/provider/Settings$System;->putInt(Landroid/content/ContentResolver;Ljava/lang/String;I)Z

    .line 98
    invoke-direct {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getCandidateApps()Ljava/util/List;

    move-result-object p1

    invoke-direct {p0, p1}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->packagesFromEntries(Ljava/util/List;)Ljava/util/List;

    move-result-object p1

    invoke-direct {p0, p1}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->savePackages(Ljava/lang/Iterable;)V

    .line 99
    invoke-direct {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->writeControlFiles()V

    .line 100
    const-string p1, "unica_scpm_allowlist_enable_all_done"

    invoke-direct {p0, p1}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->toast(Ljava/lang/String;)V

    .line 101
    invoke-direct {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->refreshState()V

    .line 102
    return v1

    .line 104
    :cond_47
    const-string v0, "unica_scpm_region_bypass_switch"

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_70

    .line 105
    iget-object p1, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mRegionBypassPreference:Landroidx/preference/SecSwitchPreference;

    invoke-virtual {p1}, Landroidx/preference/SecSwitchPreference;->isChecked()Z

    move-result p1

    .line 106
    iget-object v0, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mContext:Landroid/content/Context;

    invoke-virtual {v0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v0

    const-string v2, "unica_scpm_region_bypass"

    invoke-static {v0, v2, p1}, Landroid/provider/Settings$System;->putInt(Landroid/content/ContentResolver;Ljava/lang/String;I)Z

    .line 107
    const-string v0, "persist.sys.unica.scpm_region_bypass"

    invoke-static {p1}, Ljava/lang/Boolean;->toString(Z)Ljava/lang/String;

    move-result-object p1

    invoke-static {v0, p1}, Landroid/os/SemSystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V

    .line 108
    invoke-direct {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->writeControlFiles()V

    .line 109
    invoke-direct {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->refreshState()V

    .line 110
    return v1

    .line 112
    :cond_70
    const-string v0, "unica_scpm_allowlist_apply"

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-eqz p1, :cond_7c

    .line 113
    invoke-direct {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->applyPolicyFiles()V

    .line 114
    return v1

    .line 116
    :cond_7c
    const/4 p1, 0x0

    return p1
.end method

.method public onResume()V
    .registers 1

    .line 82
    invoke-super {p0}, Lcom/android/settings/SettingsPreferenceFragment;->onResume()V

    .line 83
    invoke-direct {p0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->refreshState()V

    .line 84
    return-void
.end method
