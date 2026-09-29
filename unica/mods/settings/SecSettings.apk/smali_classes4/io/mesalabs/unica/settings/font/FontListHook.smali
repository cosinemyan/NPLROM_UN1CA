.class public final Lio/mesalabs/unica/settings/font/FontListHook;
.super Ljava/lang/Object;
.source "FontListHook.java"


# direct methods
.method private constructor <init>()V
    .registers 1

    .line 12
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public static appendSelectedFonts(Lcom/samsung/android/settings/flipfont/FontListAdapter;)V
    .registers 5

    .line 24
    if-eqz p0, :cond_5e

    :try_start_2
    iget-object v0, p0, Lcom/samsung/android/settings/flipfont/FontListAdapter;->mContext:Landroid/content/Context;

    if-eqz v0, :cond_5e

    iget-object v0, p0, Lcom/samsung/android/settings/flipfont/FontListAdapter;->mFontNames:Ljava/util/ArrayList;

    if-eqz v0, :cond_5e

    iget-object v0, p0, Lcom/samsung/android/settings/flipfont/FontListAdapter;->mTypefaceFiles:Ljava/util/ArrayList;

    if-eqz v0, :cond_5e

    iget-object v0, p0, Lcom/samsung/android/settings/flipfont/FontListAdapter;->mFontPackageNames:Ljava/util/ArrayList;

    if-nez v0, :cond_13

    goto :goto_5e

    .line 27
    :cond_13
    iget-object v0, p0, Lcom/samsung/android/settings/flipfont/FontListAdapter;->mContext:Landroid/content/Context;

    invoke-static {v0}, Lio/mesalabs/unica/settings/font/FontBank;->getSelectedXmls(Landroid/content/Context;)Ljava/util/LinkedHashSet;

    move-result-object v0

    .line 28
    invoke-virtual {v0}, Ljava/util/LinkedHashSet;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :cond_1d
    :goto_1d
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_54

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Ljava/lang/String;

    .line 29
    invoke-static {v1}, Lio/mesalabs/unica/settings/font/FontBank;->loadEntry(Ljava/lang/String;)Lio/mesalabs/unica/settings/font/FontEntry;

    move-result-object v1

    .line 30
    if-eqz v1, :cond_1d

    iget-object v2, p0, Lcom/samsung/android/settings/flipfont/FontListAdapter;->mTypefaceFiles:Ljava/util/ArrayList;

    iget-object v3, v1, Lio/mesalabs/unica/settings/font/FontEntry;->xmlName:Ljava/lang/String;

    invoke-virtual {v2, v3}, Ljava/util/ArrayList;->contains(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_3a

    .line 31
    goto :goto_1d

    .line 33
    :cond_3a
    iget-object v2, p0, Lcom/samsung/android/settings/flipfont/FontListAdapter;->mFontNames:Ljava/util/ArrayList;

    iget-object v3, v1, Lio/mesalabs/unica/settings/font/FontEntry;->displayName:Ljava/lang/String;

    invoke-virtual {v2, v3}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    .line 34
    iget-object v2, p0, Lcom/samsung/android/settings/flipfont/FontListAdapter;->mTypefaceFiles:Ljava/util/ArrayList;

    iget-object v3, v1, Lio/mesalabs/unica/settings/font/FontEntry;->xmlName:Ljava/lang/String;

    invoke-virtual {v2, v3}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    .line 35
    iget-object v2, p0, Lcom/samsung/android/settings/flipfont/FontListAdapter;->mFontPackageNames:Ljava/util/ArrayList;

    iget-object v1, v1, Lio/mesalabs/unica/settings/font/FontEntry;->xmlName:Ljava/lang/String;

    invoke-static {v1}, Lio/mesalabs/unica/settings/font/FontBank;->toVirtualPackage(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v2, v1}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
    :try_end_53
    .catchall {:try_start_2 .. :try_end_53} :catchall_55

    .line 36
    goto :goto_1d

    .line 39
    :cond_54
    goto :goto_5d

    .line 37
    :catchall_55
    move-exception p0

    .line 38
    const-string v0, "UnicaFontSelector"

    const-string v1, "Unable to append UN1CA fonts"

    invoke-static {v0, v1, p0}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 40
    :goto_5d
    return-void

    .line 25
    :cond_5e
    :goto_5e
    return-void
.end method

.method public static applyUnicaFont(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)Z
    .registers 6

    .line 59
    const/4 p0, 0x0

    :try_start_1
    invoke-static {p1}, Lio/mesalabs/unica/settings/font/FontBank;->loadEntry(Ljava/lang/String;)Lio/mesalabs/unica/settings/font/FontEntry;

    move-result-object p1

    .line 60
    if-nez p1, :cond_8

    .line 61
    return p0

    .line 63
    :cond_8
    new-instance v0, Lcom/samsung/android/fontutil/FontWriter;

    invoke-direct {v0}, Lcom/samsung/android/fontutil/FontWriter;-><init>()V

    .line 64
    iget-object v1, p1, Lio/mesalabs/unica/settings/font/FontEntry;->directoryName:Ljava/lang/String;

    invoke-virtual {v0, v1}, Lcom/samsung/android/fontutil/FontWriter;->createFontDirectory(Ljava/lang/String;)Ljava/io/File;

    move-result-object v1

    .line 65
    if-nez v1, :cond_16

    .line 66
    return p0

    .line 68
    :cond_16
    invoke-static {v0, v1, p1}, Lio/mesalabs/unica/settings/font/FontBank;->copyFontToDirectory(Lcom/samsung/android/fontutil/FontWriter;Ljava/io/File;Lio/mesalabs/unica/settings/font/FontEntry;)Z

    move-result v2

    if-nez v2, :cond_1d

    .line 69
    return p0

    .line 71
    :cond_1d
    iget-object p1, p1, Lio/mesalabs/unica/settings/font/FontEntry;->directoryName:Ljava/lang/String;

    invoke-virtual {v0, p1}, Lcom/samsung/android/fontutil/FontWriter;->deleteFontDirectory(Ljava/lang/String;)V

    .line 72
    new-instance p1, Ljava/lang/StringBuilder;

    invoke-direct {p1}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v1}, Ljava/io/File;->getAbsolutePath()Ljava/lang/String;

    move-result-object v1

    invoke-virtual {p1, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p1

    const-string v1, "#"

    invoke-virtual {p1, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p1, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-virtual {v0, p1}, Lcom/samsung/android/fontutil/FontWriter;->writeLoc(Ljava/lang/String;)V
    :try_end_40
    .catchall {:try_start_1 .. :try_end_40} :catchall_42

    .line 73
    const/4 p0, 0x1

    return p0

    .line 74
    :catchall_42
    move-exception p1

    .line 75
    const-string p2, "UnicaFontSelector"

    const-string v0, "Unable to apply UN1CA font"

    invoke-static {p2, v0, p1}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 76
    return p0
.end method

.method public static getFont(Lcom/samsung/android/settings/flipfont/FontListAdapter;Ljava/lang/String;Ljava/lang/String;)Landroid/graphics/Typeface;
    .registers 5

    .line 43
    invoke-static {p2}, Lio/mesalabs/unica/settings/font/FontBank;->isUnicaFontPackage(Ljava/lang/String;)Z

    move-result v0

    const/4 v1, 0x0

    if-nez v0, :cond_8

    .line 44
    return-object v1

    .line 47
    :cond_8
    if-nez p0, :cond_c

    move-object p0, v1

    goto :goto_e

    :cond_c
    :try_start_c
    iget-object p0, p0, Lcom/samsung/android/settings/flipfont/FontListAdapter;->mContext:Landroid/content/Context;

    .line 48
    :goto_e
    invoke-static {p1, p2}, Lio/mesalabs/unica/settings/font/FontBank;->xmlFromPackageOrFile(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object p1

    .line 49
    invoke-static {p1}, Lio/mesalabs/unica/settings/font/FontBank;->loadEntry(Ljava/lang/String;)Lio/mesalabs/unica/settings/font/FontEntry;

    move-result-object p1

    .line 50
    invoke-static {p0, p1}, Lio/mesalabs/unica/settings/font/FontBank;->getPreviewTypeface(Landroid/content/Context;Lio/mesalabs/unica/settings/font/FontEntry;)Landroid/graphics/Typeface;

    move-result-object p0
    :try_end_1a
    .catchall {:try_start_c .. :try_end_1a} :catchall_1b

    return-object p0

    .line 51
    :catchall_1b
    move-exception p0

    .line 52
    const-string p1, "UnicaFontSelector"

    const-string p2, "Unable to load UN1CA font preview"

    invoke-static {p1, p2, p0}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 53
    return-object v1
.end method

.method public static invalidateFontList()V
    .registers 1

    .line 15
    const/4 v0, 0x0

    sput-object v0, Lcom/samsung/android/settings/flipfont/FontListAdapter;->mFontListAdapter:Lcom/samsung/android/settings/flipfont/FontListAdapter;

    .line 16
    return-void
.end method

.method public static isUnicaFontPackage(Ljava/lang/String;)Z
    .registers 1

    .line 19
    invoke-static {p0}, Lio/mesalabs/unica/settings/font/FontBank;->isUnicaFontPackage(Ljava/lang/String;)Z

    move-result p0

    return p0
.end method
