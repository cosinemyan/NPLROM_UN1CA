.class public final Lio/mesalabs/unica/settings/font/FontSelectorFragment;
.super Lcom/android/settings/dashboard/DashboardFragment;
.source "FontSelectorFragment.java"

# interfaces
.implements Landroidx/preference/Preference$OnPreferenceClickListener;
.implements Landroidx/preference/Preference$OnPreferenceChangeListener;


# static fields
.field private static final PREF_LIST:Ljava/lang/String; = "unica_font_selector_list"

.field private static final PREF_SEARCH:Ljava/lang/String; = "unica_font_selector_search"

.field static final PREVIEW_TEXT:Ljava/lang/String; = "NPL One UI 9 Official by Cosine"

.field public static final SEARCH_INDEX_DATA_PROVIDER:Lcom/android/settings/search/BaseSearchIndexProvider;


# instance fields
.field private mContext:Landroid/content/Context;

.field private mEntries:Ljava/util/List;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/List<",
            "Lio/mesalabs/unica/settings/font/FontEntry;",
            ">;"
        }
    .end annotation
.end field

.field private mSearchQuery:Ljava/lang/String;


# direct methods
.method static constructor <clinit>()V
    .registers 3

    .line 19
    new-instance v0, Lcom/android/settings/search/BaseSearchIndexProvider;

    const-string v1, "xml"

    const-string v2, "unica_font_selector_settings"

    invoke-static {v1, v2}, Lio/mesalabs/unica/utils/Utils;->getResourceId(Ljava/lang/String;Ljava/lang/String;)I

    move-result v1

    invoke-direct {v0, v1}, Lcom/android/settings/search/BaseSearchIndexProvider;-><init>(I)V

    sput-object v0, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->SEARCH_INDEX_DATA_PROVIDER:Lcom/android/settings/search/BaseSearchIndexProvider;

    return-void
.end method

.method public constructor <init>()V
    .registers 2

    .line 15
    invoke-direct {p0}, Lcom/android/settings/dashboard/DashboardFragment;-><init>()V

    .line 23
    const-string v0, ""

    iput-object v0, p0, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->mSearchQuery:Ljava/lang/String;

    return-void
.end method

.method private bindSearchPreference()V
    .registers 3

    .line 50
    const-string v0, "unica_font_selector_search"

    invoke-virtual {p0, v0}, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->findPreference(Ljava/lang/CharSequence;)Landroidx/preference/Preference;

    move-result-object v0

    .line 51
    instance-of v1, v0, Landroidx/preference/SecEditTextPreference;

    if-eqz v1, :cond_1b

    .line 52
    check-cast v0, Landroidx/preference/SecEditTextPreference;

    .line 53
    const/4 v1, 0x0

    invoke-virtual {v0, v1}, Landroidx/preference/SecEditTextPreference;->setPersistent(Z)V

    .line 54
    iget-object v1, p0, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->mSearchQuery:Ljava/lang/String;

    invoke-virtual {v0, v1}, Landroidx/preference/SecEditTextPreference;->setText(Ljava/lang/String;)V

    .line 55
    invoke-virtual {v0, p0}, Landroidx/preference/SecEditTextPreference;->setOnPreferenceChangeListener(Landroidx/preference/Preference$OnPreferenceChangeListener;)V

    .line 56
    invoke-direct {p0, v0}, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->updateSearchSummary(Landroidx/preference/SecEditTextPreference;)V

    .line 58
    :cond_1b
    return-void
.end method

.method private populateFontList()V
    .registers 10

    .line 69
    const-string v0, "unica_font_selector_list"

    invoke-virtual {p0, v0}, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->findPreference(Ljava/lang/CharSequence;)Landroidx/preference/Preference;

    move-result-object v0

    .line 70
    instance-of v1, v0, Landroidx/preference/PreferenceCategory;

    if-nez v1, :cond_b

    .line 71
    return-void

    .line 73
    :cond_b
    check-cast v0, Landroidx/preference/PreferenceCategory;

    .line 74
    invoke-virtual {v0}, Landroidx/preference/PreferenceCategory;->removeAll()V

    .line 75
    iget-object v1, p0, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->mContext:Landroid/content/Context;

    if-nez v1, :cond_1a

    .line 76
    invoke-virtual {p0}, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->getContext()Landroid/content/Context;

    move-result-object v1

    iput-object v1, p0, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->mContext:Landroid/content/Context;

    .line 78
    :cond_1a
    iget-object v1, p0, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->mContext:Landroid/content/Context;

    if-nez v1, :cond_1f

    .line 79
    return-void

    .line 81
    :cond_1f
    invoke-static {}, Lio/mesalabs/unica/settings/font/FontBank;->isSourceAvailable()Z

    move-result v1

    const/4 v2, 0x0

    if-nez v1, :cond_3f

    .line 82
    new-instance v1, Landroidx/preference/Preference;

    iget-object v3, p0, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->mContext:Landroid/content/Context;

    invoke-direct {v1, v3}, Landroidx/preference/Preference;-><init>(Landroid/content/Context;)V

    .line 83
    const-string v3, "string"

    const-string v4, "unica_font_selector_missing"

    invoke-static {v3, v4}, Lio/mesalabs/unica/utils/Utils;->getResourceId(Ljava/lang/String;Ljava/lang/String;)I

    move-result v3

    invoke-virtual {v1, v3}, Landroidx/preference/Preference;->setTitle(I)V

    .line 84
    invoke-virtual {v1, v2}, Landroidx/preference/Preference;->setEnabled(Z)V

    .line 85
    invoke-virtual {v0, v1}, Landroidx/preference/PreferenceCategory;->addPreference(Landroidx/preference/Preference;)Z

    .line 86
    return-void

    .line 88
    :cond_3f
    iget-object v1, p0, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->mEntries:Ljava/util/List;

    .line 89
    if-nez v1, :cond_49

    .line 90
    invoke-static {}, Lio/mesalabs/unica/settings/font/FontBank;->loadEntries()Ljava/util/List;

    move-result-object v1

    .line 91
    iput-object v1, p0, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->mEntries:Ljava/util/List;

    .line 93
    :cond_49
    iget-object v3, p0, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->mContext:Landroid/content/Context;

    invoke-static {v3}, Lio/mesalabs/unica/settings/font/FontBank;->getSelectedXmls(Landroid/content/Context;)Ljava/util/LinkedHashSet;

    move-result-object v3

    .line 94
    move v4, v2

    :goto_50
    invoke-interface {v1}, Ljava/util/List;->size()I

    move-result v5

    if-ge v4, v5, :cond_a6

    .line 95
    invoke-interface {v1, v4}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v5

    check-cast v5, Lio/mesalabs/unica/settings/font/FontEntry;

    .line 96
    iget-object v6, p0, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->mSearchQuery:Ljava/lang/String;

    invoke-virtual {v5, v6}, Lio/mesalabs/unica/settings/font/FontEntry;->matches(Ljava/lang/String;)Z

    move-result v6

    if-nez v6, :cond_65

    .line 97
    goto :goto_a3

    .line 99
    :cond_65
    new-instance v6, Lio/mesalabs/unica/settings/font/FontPreviewPreference;

    iget-object v7, p0, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->mContext:Landroid/content/Context;

    invoke-direct {v6, v7}, Lio/mesalabs/unica/settings/font/FontPreviewPreference;-><init>(Landroid/content/Context;)V

    .line 100
    invoke-virtual {v6, v5}, Lio/mesalabs/unica/settings/font/FontPreviewPreference;->setEntry(Lio/mesalabs/unica/settings/font/FontEntry;)V

    .line 101
    new-instance v7, Ljava/lang/StringBuilder;

    invoke-direct {v7}, Ljava/lang/StringBuilder;-><init>()V

    const-string v8, "unica_font_selector:"

    invoke-virtual {v7, v8}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v7

    iget-object v8, v5, Lio/mesalabs/unica/settings/font/FontEntry;->xmlName:Ljava/lang/String;

    invoke-virtual {v7, v8}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v7

    invoke-virtual {v7}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v7

    invoke-virtual {v6, v7}, Lio/mesalabs/unica/settings/font/FontPreviewPreference;->setKey(Ljava/lang/String;)V

    .line 102
    iget-object v7, v5, Lio/mesalabs/unica/settings/font/FontEntry;->displayName:Ljava/lang/String;

    invoke-virtual {v6, v7}, Lio/mesalabs/unica/settings/font/FontPreviewPreference;->setTitle(Ljava/lang/CharSequence;)V

    .line 103
    const-string v7, "NPL One UI 9 Official by Cosine"

    invoke-virtual {v6, v7}, Lio/mesalabs/unica/settings/font/FontPreviewPreference;->setSummary(Ljava/lang/CharSequence;)V

    .line 104
    invoke-virtual {v6, v2}, Lio/mesalabs/unica/settings/font/FontPreviewPreference;->setPersistent(Z)V

    .line 105
    iget-object v5, v5, Lio/mesalabs/unica/settings/font/FontEntry;->xmlName:Ljava/lang/String;

    invoke-virtual {v3, v5}, Ljava/util/LinkedHashSet;->contains(Ljava/lang/Object;)Z

    move-result v5

    invoke-virtual {v6, v5}, Lio/mesalabs/unica/settings/font/FontPreviewPreference;->setChecked(Z)V

    .line 106
    invoke-virtual {v6, p0}, Lio/mesalabs/unica/settings/font/FontPreviewPreference;->setOnPreferenceClickListener(Landroidx/preference/Preference$OnPreferenceClickListener;)V

    .line 107
    invoke-virtual {v0, v6}, Landroidx/preference/PreferenceCategory;->addPreference(Landroidx/preference/Preference;)Z

    .line 94
    :goto_a3
    add-int/lit8 v4, v4, 0x1

    goto :goto_50

    .line 109
    :cond_a6
    return-void
.end method

.method private updateSearchSummary(Landroidx/preference/SecEditTextPreference;)V
    .registers 4

    .line 61
    iget-object v0, p0, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->mSearchQuery:Ljava/lang/String;

    if-eqz v0, :cond_10

    invoke-virtual {v0}, Ljava/lang/String;->length()I

    move-result v0

    if-lez v0, :cond_10

    .line 62
    iget-object v0, p0, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->mSearchQuery:Ljava/lang/String;

    invoke-virtual {p1, v0}, Landroidx/preference/SecEditTextPreference;->setSummary(Ljava/lang/CharSequence;)V

    goto :goto_1b

    .line 64
    :cond_10
    const-string v0, "string"

    const-string v1, "unica_font_selector_search_summary"

    invoke-static {v0, v1}, Lio/mesalabs/unica/utils/Utils;->getResourceId(Ljava/lang/String;Ljava/lang/String;)I

    move-result v0

    invoke-virtual {p1, v0}, Landroidx/preference/SecEditTextPreference;->setSummary(I)V

    .line 66
    :goto_1b
    return-void
.end method


# virtual methods
.method public getLogTag()Ljava/lang/String;
    .registers 2

    .line 27
    const-string v0, "UnicaFontSelector"

    return-object v0
.end method

.method public getMetricsCategory()I
    .registers 2

    .line 32
    const/16 v0, 0x2e8

    return v0
.end method

.method public getPreferenceScreenResId()I
    .registers 3

    .line 37
    const-string v0, "xml"

    const-string v1, "unica_font_selector_settings"

    invoke-static {v0, v1}, Lio/mesalabs/unica/utils/Utils;->getResourceId(Ljava/lang/String;Ljava/lang/String;)I

    move-result v0

    return v0
.end method

.method public onCreate(Landroid/os/Bundle;)V
    .registers 2

    .line 42
    invoke-super {p0, p1}, Lcom/android/settings/dashboard/DashboardFragment;->onCreate(Landroid/os/Bundle;)V

    .line 43
    invoke-virtual {p0}, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->getContext()Landroid/content/Context;

    move-result-object p1

    iput-object p1, p0, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->mContext:Landroid/content/Context;

    .line 44
    invoke-static {}, Lio/mesalabs/unica/settings/font/FontBank;->loadEntries()Ljava/util/List;

    move-result-object p1

    iput-object p1, p0, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->mEntries:Ljava/util/List;

    .line 45
    invoke-direct {p0}, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->bindSearchPreference()V

    .line 46
    invoke-direct {p0}, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->populateFontList()V

    .line 47
    return-void
.end method

.method public onPreferenceChange(Landroidx/preference/Preference;Ljava/lang/Object;)Z
    .registers 5

    .line 113
    if-eqz p1, :cond_2f

    const-string v0, "unica_font_selector_search"

    invoke-virtual {p1}, Landroidx/preference/Preference;->getKey()Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_f

    goto :goto_2f

    .line 116
    :cond_f
    if-nez p2, :cond_14

    const-string p2, ""

    goto :goto_1c

    :cond_14
    invoke-virtual {p2}, Ljava/lang/Object;->toString()Ljava/lang/String;

    move-result-object p2

    invoke-virtual {p2}, Ljava/lang/String;->trim()Ljava/lang/String;

    move-result-object p2

    :goto_1c
    iput-object p2, p0, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->mSearchQuery:Ljava/lang/String;

    .line 117
    instance-of v0, p1, Landroidx/preference/SecEditTextPreference;

    if-eqz v0, :cond_2a

    .line 118
    check-cast p1, Landroidx/preference/SecEditTextPreference;

    .line 119
    invoke-virtual {p1, p2}, Landroidx/preference/SecEditTextPreference;->setText(Ljava/lang/String;)V

    .line 120
    invoke-direct {p0, p1}, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->updateSearchSummary(Landroidx/preference/SecEditTextPreference;)V

    .line 122
    :cond_2a
    invoke-direct {p0}, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->populateFontList()V

    .line 123
    const/4 p1, 0x1

    return p1

    .line 114
    :cond_2f
    :goto_2f
    const/4 p1, 0x0

    return p1
.end method

.method public onPreferenceClick(Landroidx/preference/Preference;)Z
    .registers 6

    .line 128
    instance-of v0, p1, Lio/mesalabs/unica/settings/font/FontPreviewPreference;

    const/4 v1, 0x0

    if-nez v0, :cond_6

    .line 129
    return v1

    .line 131
    :cond_6
    check-cast p1, Lio/mesalabs/unica/settings/font/FontPreviewPreference;

    .line 132
    invoke-virtual {p1}, Lio/mesalabs/unica/settings/font/FontPreviewPreference;->getEntry()Lio/mesalabs/unica/settings/font/FontEntry;

    move-result-object v0

    .line 133
    if-eqz v0, :cond_54

    iget-object v2, p0, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->mContext:Landroid/content/Context;

    if-nez v2, :cond_13

    goto :goto_54

    .line 136
    :cond_13
    invoke-static {v2}, Lio/mesalabs/unica/settings/font/FontBank;->getSelectedXmls(Landroid/content/Context;)Ljava/util/LinkedHashSet;

    move-result-object v2

    .line 137
    invoke-virtual {p1}, Lio/mesalabs/unica/settings/font/FontPreviewPreference;->isChecked()Z

    move-result v3

    if-eqz v3, :cond_23

    .line 138
    iget-object v0, v0, Lio/mesalabs/unica/settings/font/FontEntry;->xmlName:Ljava/lang/String;

    invoke-virtual {v2, v0}, Ljava/util/LinkedHashSet;->add(Ljava/lang/Object;)Z

    goto :goto_28

    .line 140
    :cond_23
    iget-object v0, v0, Lio/mesalabs/unica/settings/font/FontEntry;->xmlName:Ljava/lang/String;

    invoke-virtual {v2, v0}, Ljava/util/LinkedHashSet;->remove(Ljava/lang/Object;)Z

    .line 142
    :goto_28
    iget-object v0, p0, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->mContext:Landroid/content/Context;

    invoke-static {v0, v2}, Lio/mesalabs/unica/settings/font/FontBank;->setSelectedXmls(Landroid/content/Context;Ljava/util/Set;)Z

    move-result v0

    .line 143
    const/4 v2, 0x1

    if-nez v0, :cond_3a

    .line 144
    invoke-virtual {p1}, Lio/mesalabs/unica/settings/font/FontPreviewPreference;->isChecked()Z

    move-result v3

    xor-int/2addr v3, v2

    invoke-virtual {p1, v3}, Lio/mesalabs/unica/settings/font/FontPreviewPreference;->setChecked(Z)V

    goto :goto_3d

    .line 146
    :cond_3a
    invoke-static {}, Lio/mesalabs/unica/settings/font/FontListHook;->invalidateFontList()V

    .line 148
    :goto_3d
    iget-object p1, p0, Lio/mesalabs/unica/settings/font/FontSelectorFragment;->mContext:Landroid/content/Context;

    if-eqz v0, :cond_44

    const-string v0, "unica_font_selector_saved"

    goto :goto_46

    :cond_44
    const-string v0, "unica_font_selector_save_failed"

    :goto_46
    const-string v3, "string"

    invoke-static {v3, v0}, Lio/mesalabs/unica/utils/Utils;->getResourceId(Ljava/lang/String;Ljava/lang/String;)I

    move-result v0

    invoke-static {p1, v0, v1}, Landroid/widget/Toast;->makeText(Landroid/content/Context;II)Landroid/widget/Toast;

    move-result-object p1

    invoke-virtual {p1}, Landroid/widget/Toast;->show()V

    .line 149
    return v2

    .line 134
    :cond_54
    :goto_54
    return v1
.end method
