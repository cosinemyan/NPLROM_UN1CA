.class final Lio/mesalabs/unica/settings/font/FontPreviewPreference;
.super Landroidx/preference/SecSwitchPreference;
.source "FontPreviewPreference.java"


# instance fields
.field private mEntry:Lio/mesalabs/unica/settings/font/FontEntry;


# direct methods
.method constructor <init>(Landroid/content/Context;)V
    .registers 2

    .line 13
    invoke-direct {p0, p1}, Landroidx/preference/SecSwitchPreference;-><init>(Landroid/content/Context;)V

    .line 14
    return-void
.end method


# virtual methods
.method getEntry()Lio/mesalabs/unica/settings/font/FontEntry;
    .registers 2

    .line 17
    iget-object v0, p0, Lio/mesalabs/unica/settings/font/FontPreviewPreference;->mEntry:Lio/mesalabs/unica/settings/font/FontEntry;

    return-object v0
.end method

.method public onBindViewHolder(Landroidx/preference/PreferenceViewHolder;)V
    .registers 4

    .line 26
    invoke-super {p0, p1}, Landroidx/preference/SecSwitchPreference;->onBindViewHolder(Landroidx/preference/PreferenceViewHolder;)V

    .line 27
    if-nez p1, :cond_6

    .line 28
    return-void

    .line 30
    :cond_6
    const v0, 0x1020010

    invoke-virtual {p1, v0}, Landroidx/preference/PreferenceViewHolder;->findViewById(I)Landroid/view/View;

    move-result-object p1

    .line 31
    instance-of v0, p1, Landroid/widget/TextView;

    if-nez v0, :cond_12

    .line 32
    return-void

    .line 34
    :cond_12
    check-cast p1, Landroid/widget/TextView;

    .line 35
    const-string v0, "NPL One UI 9 Official by Cosine"

    invoke-virtual {p1, v0}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    .line 36
    invoke-virtual {p0}, Lio/mesalabs/unica/settings/font/FontPreviewPreference;->getContext()Landroid/content/Context;

    move-result-object v0

    iget-object v1, p0, Lio/mesalabs/unica/settings/font/FontPreviewPreference;->mEntry:Lio/mesalabs/unica/settings/font/FontEntry;

    invoke-static {v0, v1}, Lio/mesalabs/unica/settings/font/FontBank;->getPreviewTypeface(Landroid/content/Context;Lio/mesalabs/unica/settings/font/FontEntry;)Landroid/graphics/Typeface;

    move-result-object v0

    .line 37
    if-eqz v0, :cond_28

    .line 38
    invoke-virtual {p1, v0}, Landroid/widget/TextView;->setTypeface(Landroid/graphics/Typeface;)V

    .line 40
    :cond_28
    return-void
.end method

.method setEntry(Lio/mesalabs/unica/settings/font/FontEntry;)V
    .registers 2

    .line 21
    iput-object p1, p0, Lio/mesalabs/unica/settings/font/FontPreviewPreference;->mEntry:Lio/mesalabs/unica/settings/font/FontEntry;

    .line 22
    return-void
.end method
