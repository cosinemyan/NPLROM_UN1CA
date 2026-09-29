.class final Lio/mesalabs/unica/settings/font/FontBank;
.super Ljava/lang/Object;
.source "FontBank.java"


# static fields
.field static final CURRENT_XML_KEY:Ljava/lang/String; = "unica_font_selector_current_xml"

.field private static final PREVIEW_CACHE:Ljava/util/LinkedHashMap;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/LinkedHashMap<",
            "Ljava/lang/String;",
            "Landroid/graphics/Typeface;",
            ">;"
        }
    .end annotation
.end field

.field private static final PREVIEW_CACHE_LIMIT:I = 0xc

.field static final SELECTED_XMLS_KEY:Ljava/lang/String; = "unica_font_selector_selected_xmls"

.field static final SOURCE_APK_PATH:Ljava/lang/String; = "/system/etc/unica/font_selector/SamsungSans.apk"

.field static final TAG:Ljava/lang/String; = "UnicaFontSelector"

.field static final VIRTUAL_PACKAGE_PREFIX:Ljava/lang/String; = "unica_font:"


# direct methods
.method static constructor <clinit>()V
    .registers 1

    .line 29
    new-instance v0, Ljava/util/LinkedHashMap;

    invoke-direct {v0}, Ljava/util/LinkedHashMap;-><init>()V

    sput-object v0, Lio/mesalabs/unica/settings/font/FontBank;->PREVIEW_CACHE:Ljava/util/LinkedHashMap;

    return-void
.end method

.method private constructor <init>()V
    .registers 1

    .line 31
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method static closeQuietly(Ljava/lang/Object;)V
    .registers 2

    .line 271
    instance-of v0, p0, Ljava/io/Closeable;

    if-eqz v0, :cond_c

    .line 273
    :try_start_4
    check-cast p0, Ljava/io/Closeable;

    invoke-interface {p0}, Ljava/io/Closeable;->close()V
    :try_end_9
    .catch Ljava/lang/Exception; {:try_start_4 .. :try_end_9} :catch_a

    goto :goto_b

    .line 274
    :catch_a
    move-exception p0

    .line 275
    :goto_b
    goto :goto_17

    .line 276
    :cond_c
    instance-of v0, p0, Ljava/util/zip/ZipFile;

    if-eqz v0, :cond_17

    .line 278
    :try_start_10
    check-cast p0, Ljava/util/zip/ZipFile;

    invoke-virtual {p0}, Ljava/util/zip/ZipFile;->close()V
    :try_end_15
    .catch Ljava/lang/Exception; {:try_start_10 .. :try_end_15} :catch_16

    .line 280
    goto :goto_17

    .line 279
    :catch_16
    move-exception p0

    .line 282
    :cond_17
    :goto_17
    return-void
.end method

.method static copyFontToDirectory(Lcom/samsung/android/fontutil/FontWriter;Ljava/io/File;Lio/mesalabs/unica/settings/font/FontEntry;)Z
    .registers 12

    .line 224
    nop

    .line 225
    nop

    .line 227
    const/4 v0, 0x0

    const/4 v1, 0x0

    :try_start_4
    new-instance v2, Ljava/util/zip/ZipFile;

    const-string v3, "/system/etc/unica/font_selector/SamsungSans.apk"

    invoke-direct {v2, v3}, Ljava/util/zip/ZipFile;-><init>(Ljava/lang/String;)V
    :try_end_b
    .catch Ljava/lang/Exception; {:try_start_4 .. :try_end_b} :catch_66
    .catchall {:try_start_4 .. :try_end_b} :catchall_63

    .line 228
    nop

    .line 229
    move v3, v0

    move v4, v3

    :goto_e
    :try_start_e
    iget-object v5, p2, Lio/mesalabs/unica/settings/font/FontEntry;->files:Ljava/util/ArrayList;

    invoke-virtual {v5}, Ljava/util/ArrayList;->size()I

    move-result v5

    const/4 v6, 0x1

    if-ge v3, v5, :cond_57

    .line 230
    iget-object v5, p2, Lio/mesalabs/unica/settings/font/FontEntry;->files:Ljava/util/ArrayList;

    invoke-virtual {v5, v3}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;

    move-result-object v5

    check-cast v5, Lio/mesalabs/unica/settings/font/FontFile;

    .line 231
    new-instance v7, Ljava/lang/StringBuilder;

    invoke-direct {v7}, Ljava/lang/StringBuilder;-><init>()V

    const-string v8, "assets/fonts/"

    invoke-virtual {v7, v8}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v7

    iget-object v8, v5, Lio/mesalabs/unica/settings/font/FontFile;->fileName:Ljava/lang/String;

    invoke-virtual {v7, v8}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v7

    invoke-virtual {v7}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v7

    invoke-virtual {v2, v7}, Ljava/util/zip/ZipFile;->getEntry(Ljava/lang/String;)Ljava/util/zip/ZipEntry;

    move-result-object v7

    .line 232
    if-nez v7, :cond_3d

    .line 233
    nop

    .line 234
    move v4, v6

    goto :goto_4e

    .line 236
    :cond_3d
    invoke-virtual {v2, v7}, Ljava/util/zip/ZipFile;->getInputStream(Ljava/util/zip/ZipEntry;)Ljava/io/InputStream;

    move-result-object v7
    :try_end_41
    .catch Ljava/lang/Exception; {:try_start_e .. :try_end_41} :catch_61
    .catchall {:try_start_e .. :try_end_41} :catchall_77

    .line 237
    :try_start_41
    iget-object v5, v5, Lio/mesalabs/unica/settings/font/FontFile;->droidName:Ljava/lang/String;

    invoke-virtual {p0, p1, v7, v5}, Lcom/samsung/android/fontutil/FontWriter;->copyFontFile(Ljava/io/File;Ljava/io/InputStream;Ljava/lang/String;)Z

    move-result v5

    if-eqz v5, :cond_4a

    .line 238
    move v4, v6

    .line 240
    :cond_4a
    invoke-static {v7}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V
    :try_end_4d
    .catch Ljava/lang/Exception; {:try_start_41 .. :try_end_4d} :catch_54
    .catchall {:try_start_41 .. :try_end_4d} :catchall_51

    .line 241
    nop

    .line 229
    :goto_4e
    add-int/lit8 v3, v3, 0x1

    goto :goto_e

    .line 248
    :catchall_51
    move-exception p0

    move-object v1, v7

    goto :goto_78

    .line 244
    :catch_54
    move-exception p0

    move-object v1, v7

    goto :goto_68

    .line 243
    :cond_57
    nop

    .line 248
    xor-int/lit8 p0, v4, 0x1

    invoke-static {v1}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V

    .line 249
    invoke-static {v2}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V

    .line 243
    return p0

    .line 244
    :catch_61
    move-exception p0

    goto :goto_68

    .line 248
    :catchall_63
    move-exception p0

    move-object v2, v1

    goto :goto_78

    .line 244
    :catch_66
    move-exception p0

    move-object v2, v1

    .line 245
    :goto_68
    :try_start_68
    const-string p1, "UnicaFontSelector"

    const-string p2, "Unable to copy UN1CA font"

    invoke-static {p1, p2, p0}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
    :try_end_6f
    .catchall {:try_start_68 .. :try_end_6f} :catchall_77

    .line 246
    nop

    .line 248
    invoke-static {v1}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V

    .line 249
    invoke-static {v2}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V

    .line 246
    return v0

    .line 248
    :catchall_77
    move-exception p0

    :goto_78
    invoke-static {v1}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V

    .line 249
    invoke-static {v2}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V

    .line 250
    throw p0
.end method

.method private static copyToFile(Ljava/io/InputStream;Ljava/io/File;)V
    .registers 6
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/lang/Exception;
        }
    .end annotation

    .line 254
    nop

    .line 255
    nop

    .line 257
    const/4 v0, 0x0

    :try_start_3
    new-instance v1, Ljava/io/FileOutputStream;

    invoke-direct {v1, p1}, Ljava/io/FileOutputStream;-><init>(Ljava/io/File;)V
    :try_end_8
    .catchall {:try_start_3 .. :try_end_8} :catchall_29

    .line 258
    :try_start_8
    new-instance p1, Ljava/io/BufferedOutputStream;

    invoke-direct {p1, v1}, Ljava/io/BufferedOutputStream;-><init>(Ljava/io/OutputStream;)V
    :try_end_d
    .catchall {:try_start_8 .. :try_end_d} :catchall_27

    .line 259
    const/16 v0, 0x2000

    :try_start_f
    new-array v0, v0, [B

    .line 261
    :goto_11
    invoke-virtual {p0, v0}, Ljava/io/InputStream;->read([B)I

    move-result v2

    if-lez v2, :cond_1c

    .line 262
    const/4 v3, 0x0

    invoke-virtual {p1, v0, v3, v2}, Ljava/io/BufferedOutputStream;->write([BII)V
    :try_end_1b
    .catchall {:try_start_f .. :try_end_1b} :catchall_24

    goto :goto_11

    .line 265
    :cond_1c
    invoke-static {p1}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V

    .line 266
    invoke-static {v1}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V

    .line 267
    nop

    .line 268
    return-void

    .line 265
    :catchall_24
    move-exception p0

    move-object v0, p1

    goto :goto_2b

    :catchall_27
    move-exception p0

    goto :goto_2b

    :catchall_29
    move-exception p0

    move-object v1, v0

    :goto_2b
    invoke-static {v0}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V

    .line 266
    invoke-static {v1}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V

    .line 267
    throw p0
.end method

.method static encodeSelectedXmls(Ljava/util/Set;)Ljava/lang/String;
    .registers 4
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/util/Set<",
            "Ljava/lang/String;",
            ">;)",
            "Ljava/lang/String;"
        }
    .end annotation

    .line 125
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    .line 126
    invoke-interface {p0}, Ljava/util/Set;->iterator()Ljava/util/Iterator;

    move-result-object p0

    .line 127
    :goto_9
    invoke-interface {p0}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_2b

    .line 128
    invoke-interface {p0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Ljava/lang/String;

    .line 129
    invoke-static {v1}, Lio/mesalabs/unica/settings/font/FontBank;->isSafeXmlName(Ljava/lang/String;)Z

    move-result v2

    if-nez v2, :cond_1c

    .line 130
    goto :goto_9

    .line 132
    :cond_1c
    invoke-virtual {v0}, Ljava/lang/StringBuilder;->length()I

    move-result v2

    if-lez v2, :cond_27

    .line 133
    const/16 v2, 0x2c

    invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;->append(C)Ljava/lang/StringBuilder;

    .line 135
    :cond_27
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 136
    goto :goto_9

    .line 137
    :cond_2b
    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    return-object p0
.end method

.method private static ensurePreviewFile(Landroid/content/Context;Lio/mesalabs/unica/settings/font/FontEntry;)Ljava/io/File;
    .registers 6
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/lang/Exception;
        }
    .end annotation

    .line 196
    new-instance v0, Ljava/io/File;

    invoke-virtual {p0}, Landroid/content/Context;->getCacheDir()Ljava/io/File;

    move-result-object p0

    const-string v1, "unica_font_selector_preview"

    invoke-direct {v0, p0, v1}, Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V

    .line 197
    invoke-virtual {v0}, Ljava/io/File;->isDirectory()Z

    move-result p0

    if-nez p0, :cond_14

    .line 198
    invoke-virtual {v0}, Ljava/io/File;->mkdirs()Z

    .line 200
    :cond_14
    new-instance p0, Ljava/io/File;

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    iget-object v2, p1, Lio/mesalabs/unica/settings/font/FontEntry;->safeName:Ljava/lang/String;

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    const-string v2, ".font"

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-direct {p0, v0, v1}, Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V

    .line 201
    invoke-virtual {p0}, Ljava/io/File;->isFile()Z

    move-result v0

    if-eqz v0, :cond_3f

    invoke-virtual {p0}, Ljava/io/File;->length()J

    move-result-wide v0

    const-wide/16 v2, 0x0

    cmp-long v0, v0, v2

    if-lez v0, :cond_3f

    .line 202
    return-object p0

    .line 204
    :cond_3f
    nop

    .line 205
    nop

    .line 207
    const/4 v0, 0x0

    :try_start_42
    new-instance v1, Ljava/util/zip/ZipFile;

    const-string v2, "/system/etc/unica/font_selector/SamsungSans.apk"

    invoke-direct {v1, v2}, Ljava/util/zip/ZipFile;-><init>(Ljava/lang/String;)V
    :try_end_49
    .catchall {:try_start_42 .. :try_end_49} :catchall_85

    .line 208
    :try_start_49
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "assets/fonts/"

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v2

    iget-object p1, p1, Lio/mesalabs/unica/settings/font/FontEntry;->previewFileName:Ljava/lang/String;

    invoke-virtual {v2, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-virtual {v1, p1}, Ljava/util/zip/ZipFile;->getEntry(Ljava/lang/String;)Ljava/util/zip/ZipEntry;

    move-result-object p1
    :try_end_62
    .catchall {:try_start_49 .. :try_end_62} :catchall_83

    .line 209
    if-nez p1, :cond_6c

    .line 210
    nop

    .line 218
    invoke-static {v0}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V

    .line 219
    invoke-static {v1}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V

    .line 210
    return-object v0

    .line 212
    :cond_6c
    :try_start_6c
    invoke-virtual {v1, p1}, Ljava/util/zip/ZipFile;->getInputStream(Ljava/util/zip/ZipEntry;)Ljava/io/InputStream;

    move-result-object v0

    .line 213
    invoke-static {v0, p0}, Lio/mesalabs/unica/settings/font/FontBank;->copyToFile(Ljava/io/InputStream;Ljava/io/File;)V

    .line 214
    const/4 p1, 0x0

    const/4 v2, 0x1

    invoke-virtual {p0, v2, p1}, Ljava/io/File;->setReadable(ZZ)Z

    .line 215
    invoke-virtual {p0, v2, v2}, Ljava/io/File;->setWritable(ZZ)Z
    :try_end_7b
    .catchall {:try_start_6c .. :try_end_7b} :catchall_83

    .line 216
    nop

    .line 218
    invoke-static {v0}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V

    .line 219
    invoke-static {v1}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V

    .line 216
    return-object p0

    .line 218
    :catchall_83
    move-exception p0

    goto :goto_87

    :catchall_85
    move-exception p0

    move-object v1, v0

    :goto_87
    invoke-static {v0}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V

    .line 219
    invoke-static {v1}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V

    .line 220
    throw p0
.end method

.method static getPreviewTypeface(Landroid/content/Context;Lio/mesalabs/unica/settings/font/FontEntry;)Landroid/graphics/Typeface;
    .registers 5

    .line 163
    const/4 v0, 0x0

    if-eqz p0, :cond_5f

    if-eqz p1, :cond_5f

    iget-object v1, p1, Lio/mesalabs/unica/settings/font/FontEntry;->previewFileName:Ljava/lang/String;

    if-nez v1, :cond_a

    goto :goto_5f

    .line 166
    :cond_a
    sget-object v1, Lio/mesalabs/unica/settings/font/FontBank;->PREVIEW_CACHE:Ljava/util/LinkedHashMap;

    monitor-enter v1

    .line 167
    :try_start_d
    iget-object v2, p1, Lio/mesalabs/unica/settings/font/FontEntry;->xmlName:Ljava/lang/String;

    invoke-virtual {v1, v2}, Ljava/util/LinkedHashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Landroid/graphics/Typeface;

    .line 168
    if-eqz v2, :cond_19

    .line 169
    monitor-exit v1

    return-object v2

    .line 171
    :cond_19
    monitor-exit v1
    :try_end_1a
    .catchall {:try_start_d .. :try_end_1a} :catchall_5c

    .line 173
    :try_start_1a
    invoke-static {p0, p1}, Lio/mesalabs/unica/settings/font/FontBank;->ensurePreviewFile(Landroid/content/Context;Lio/mesalabs/unica/settings/font/FontEntry;)Ljava/io/File;

    move-result-object p0

    .line 174
    if-eqz p0, :cond_52

    invoke-virtual {p0}, Ljava/io/File;->isFile()Z

    move-result v2

    if-nez v2, :cond_27

    goto :goto_52

    .line 177
    :cond_27
    invoke-static {p0}, Landroid/graphics/Typeface;->createFromFile(Ljava/io/File;)Landroid/graphics/Typeface;

    move-result-object p0

    .line 178
    monitor-enter v1
    :try_end_2c
    .catchall {:try_start_1a .. :try_end_2c} :catchall_53

    .line 179
    :try_start_2c
    iget-object p1, p1, Lio/mesalabs/unica/settings/font/FontEntry;->xmlName:Ljava/lang/String;

    invoke-virtual {v1, p1, p0}, Ljava/util/LinkedHashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    .line 180
    invoke-virtual {v1}, Ljava/util/LinkedHashMap;->size()I

    move-result p1

    const/16 v2, 0xc

    if-le p1, v2, :cond_4d

    .line 181
    invoke-virtual {v1}, Ljava/util/LinkedHashMap;->keySet()Ljava/util/Set;

    move-result-object p1

    invoke-interface {p1}, Ljava/util/Set;->iterator()Ljava/util/Iterator;

    move-result-object p1

    .line 182
    invoke-interface {p1}, Ljava/util/Iterator;->hasNext()Z

    move-result v2

    if-eqz v2, :cond_4d

    .line 183
    invoke-interface {p1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    .line 184
    invoke-interface {p1}, Ljava/util/Iterator;->remove()V

    .line 187
    :cond_4d
    monitor-exit v1

    .line 188
    return-object p0

    .line 187
    :catchall_4f
    move-exception p0

    monitor-exit v1
    :try_end_51
    .catchall {:try_start_2c .. :try_end_51} :catchall_4f

    :try_start_51
    throw p0
    :try_end_52
    .catchall {:try_start_51 .. :try_end_52} :catchall_53

    .line 175
    :cond_52
    :goto_52
    return-object v0

    .line 189
    :catchall_53
    move-exception p0

    .line 190
    const-string p1, "UnicaFontSelector"

    const-string v1, "Unable to load font preview"

    invoke-static {p1, v1, p0}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 191
    return-object v0

    .line 171
    :catchall_5c
    move-exception p0

    :try_start_5d
    monitor-exit v1
    :try_end_5e
    .catchall {:try_start_5d .. :try_end_5e} :catchall_5c

    throw p0

    .line 164
    :cond_5f
    :goto_5f
    return-object v0
.end method

.method static getSelectedXmls(Landroid/content/Context;)Ljava/util/LinkedHashSet;
    .registers 5
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Landroid/content/Context;",
            ")",
            "Ljava/util/LinkedHashSet<",
            "Ljava/lang/String;",
            ">;"
        }
    .end annotation

    .line 95
    new-instance v0, Ljava/util/LinkedHashSet;

    invoke-direct {v0}, Ljava/util/LinkedHashSet;-><init>()V

    .line 96
    if-nez p0, :cond_8

    .line 97
    return-object v0

    .line 99
    :cond_8
    invoke-virtual {p0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v1

    const-string v2, "unica_font_selector_selected_xmls"

    invoke-static {v1, v2}, Landroid/provider/Settings$Global;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    .line 100
    if-eqz v1, :cond_38

    invoke-virtual {v1}, Ljava/lang/String;->length()I

    move-result v2

    if-nez v2, :cond_1b

    goto :goto_38

    .line 107
    :cond_1b
    const-string p0, ","

    invoke-virtual {v1, p0}, Ljava/lang/String;->split(Ljava/lang/String;)[Ljava/lang/String;

    move-result-object p0

    .line 108
    const/4 v1, 0x0

    :goto_22
    array-length v2, p0

    if-ge v1, v2, :cond_37

    .line 109
    aget-object v2, p0, v1

    invoke-virtual {v2}, Ljava/lang/String;->trim()Ljava/lang/String;

    move-result-object v2

    .line 110
    invoke-static {v2}, Lio/mesalabs/unica/settings/font/FontBank;->isSafeXmlName(Ljava/lang/String;)Z

    move-result v3

    if-eqz v3, :cond_34

    .line 111
    invoke-virtual {v0, v2}, Ljava/util/LinkedHashSet;->add(Ljava/lang/Object;)Z

    .line 108
    :cond_34
    add-int/lit8 v1, v1, 0x1

    goto :goto_22

    .line 114
    :cond_37
    return-object v0

    .line 101
    :cond_38
    :goto_38
    invoke-virtual {p0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object p0

    const-string v1, "unica_font_selector_current_xml"

    invoke-static {p0, v1}, Landroid/provider/Settings$Global;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;

    move-result-object p0

    .line 102
    invoke-static {p0}, Lio/mesalabs/unica/settings/font/FontBank;->isSafeXmlName(Ljava/lang/String;)Z

    move-result v1

    if-eqz v1, :cond_4b

    .line 103
    invoke-virtual {v0, p0}, Ljava/util/LinkedHashSet;->add(Ljava/lang/Object;)Z

    .line 105
    :cond_4b
    return-object v0
.end method

.method static isSafeXmlName(Ljava/lang/String;)Z
    .registers 2

    .line 141
    if-eqz p0, :cond_24

    const-string v0, ".xml"

    invoke-virtual {p0, v0}, Ljava/lang/String;->endsWith(Ljava/lang/String;)Z

    move-result v0

    if-eqz v0, :cond_24

    const/16 v0, 0x2f

    invoke-virtual {p0, v0}, Ljava/lang/String;->indexOf(I)I

    move-result v0

    if-gez v0, :cond_24

    const/16 v0, 0x5c

    invoke-virtual {p0, v0}, Ljava/lang/String;->indexOf(I)I

    move-result v0

    if-ltz v0, :cond_1b

    goto :goto_24

    .line 144
    :cond_1b
    invoke-static {p0}, Lio/mesalabs/unica/settings/font/FontEntry;->sanitize(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p0

    return p0

    .line 142
    :cond_24
    :goto_24
    const/4 p0, 0x0

    return p0
.end method

.method static isSourceAvailable()Z
    .registers 2

    .line 34
    new-instance v0, Ljava/io/File;

    const-string v1, "/system/etc/unica/font_selector/SamsungSans.apk"

    invoke-direct {v0, v1}, Ljava/io/File;-><init>(Ljava/lang/String;)V

    invoke-virtual {v0}, Ljava/io/File;->isFile()Z

    move-result v0

    return v0
.end method

.method static isUnicaFontPackage(Ljava/lang/String;)Z
    .registers 2

    .line 152
    if-eqz p0, :cond_c

    const-string v0, "unica_font:"

    invoke-virtual {p0, v0}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result p0

    if-eqz p0, :cond_c

    const/4 p0, 0x1

    goto :goto_d

    :cond_c
    const/4 p0, 0x0

    :goto_d
    return p0
.end method

.method static loadEntries()Ljava/util/List;
    .registers 10
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()",
            "Ljava/util/List<",
            "Lio/mesalabs/unica/settings/font/FontEntry;",
            ">;"
        }
    .end annotation

    .line 38
    const-string v0, "UnicaFontSelector"

    new-instance v1, Ljava/util/ArrayList;

    invoke-direct {v1}, Ljava/util/ArrayList;-><init>()V

    .line 39
    nop

    .line 41
    const/4 v2, 0x0

    :try_start_9
    new-instance v3, Ljava/util/zip/ZipFile;

    const-string v4, "/system/etc/unica/font_selector/SamsungSans.apk"

    invoke-direct {v3, v4}, Ljava/util/zip/ZipFile;-><init>(Ljava/lang/String;)V
    :try_end_10
    .catch Ljava/lang/Exception; {:try_start_9 .. :try_end_10} :catch_6b
    .catchall {:try_start_9 .. :try_end_10} :catchall_69

    .line 42
    :try_start_10
    invoke-virtual {v3}, Ljava/util/zip/ZipFile;->entries()Ljava/util/Enumeration;

    move-result-object v4

    invoke-static {v4}, Ljava/util/Collections;->list(Ljava/util/Enumeration;)Ljava/util/ArrayList;

    move-result-object v4

    .line 43
    const/4 v5, 0x0

    :goto_19
    invoke-virtual {v4}, Ljava/util/ArrayList;->size()I

    move-result v6

    if-ge v5, v6, :cond_74

    .line 44
    invoke-virtual {v4, v5}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;

    move-result-object v6

    check-cast v6, Ljava/util/zip/ZipEntry;

    .line 45
    invoke-virtual {v6}, Ljava/util/zip/ZipEntry;->getName()Ljava/lang/String;

    move-result-object v7

    .line 46
    const-string v8, "assets/xml/"

    invoke-virtual {v7, v8}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v8

    if-eqz v8, :cond_64

    const-string v8, ".xml"

    invoke-virtual {v7, v8}, Ljava/lang/String;->endsWith(Ljava/lang/String;)Z

    move-result v8
    :try_end_37
    .catch Ljava/lang/Exception; {:try_start_10 .. :try_end_37} :catch_67
    .catchall {:try_start_10 .. :try_end_37} :catchall_79

    if-nez v8, :cond_3a

    .line 47
    goto :goto_64

    .line 49
    :cond_3a
    nop

    .line 51
    :try_start_3b
    invoke-virtual {v3, v6}, Ljava/util/zip/ZipFile;->getInputStream(Ljava/util/zip/ZipEntry;)Ljava/io/InputStream;

    move-result-object v6
    :try_end_3f
    .catch Ljava/lang/Exception; {:try_start_3b .. :try_end_3f} :catch_58
    .catchall {:try_start_3b .. :try_end_3f} :catchall_54

    .line 52
    :try_start_3f
    invoke-static {v7, v6}, Lio/mesalabs/unica/settings/font/FontEntry;->parse(Ljava/lang/String;Ljava/io/InputStream;)Lio/mesalabs/unica/settings/font/FontEntry;

    move-result-object v7

    .line 53
    invoke-virtual {v7}, Lio/mesalabs/unica/settings/font/FontEntry;->isUsable()Z

    move-result v8

    if-eqz v8, :cond_4c

    .line 54
    invoke-virtual {v1, v7}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
    :try_end_4c
    .catch Ljava/lang/Exception; {:try_start_3f .. :try_end_4c} :catch_52
    .catchall {:try_start_3f .. :try_end_4c} :catchall_50

    .line 59
    :cond_4c
    :goto_4c
    :try_start_4c
    invoke-static {v6}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V
    :try_end_4f
    .catch Ljava/lang/Exception; {:try_start_4c .. :try_end_4f} :catch_67
    .catchall {:try_start_4c .. :try_end_4f} :catchall_79

    .line 60
    goto :goto_64

    .line 59
    :catchall_50
    move-exception v2

    goto :goto_60

    .line 56
    :catch_52
    move-exception v7

    goto :goto_5a

    .line 59
    :catchall_54
    move-exception v4

    move-object v6, v2

    move-object v2, v4

    goto :goto_60

    .line 56
    :catch_58
    move-exception v7

    move-object v6, v2

    .line 57
    :goto_5a
    :try_start_5a
    const-string v8, "Skipping font descriptor"

    invoke-static {v0, v8, v7}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
    :try_end_5f
    .catchall {:try_start_5a .. :try_end_5f} :catchall_50

    goto :goto_4c

    .line 59
    :goto_60
    :try_start_60
    invoke-static {v6}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V

    .line 60
    throw v2
    :try_end_64
    .catch Ljava/lang/Exception; {:try_start_60 .. :try_end_64} :catch_67
    .catchall {:try_start_60 .. :try_end_64} :catchall_79

    .line 43
    :cond_64
    :goto_64
    add-int/lit8 v5, v5, 0x1

    goto :goto_19

    .line 62
    :catch_67
    move-exception v2

    goto :goto_6f

    .line 65
    :catchall_69
    move-exception v0

    goto :goto_7b

    .line 62
    :catch_6b
    move-exception v3

    move-object v9, v3

    move-object v3, v2

    move-object v2, v9

    .line 63
    :goto_6f
    :try_start_6f
    const-string v4, "Unable to load SamsungSans source bank"

    invoke-static {v0, v4, v2}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
    :try_end_74
    .catchall {:try_start_6f .. :try_end_74} :catchall_79

    .line 65
    :cond_74
    invoke-static {v3}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V

    .line 66
    nop

    .line 67
    return-object v1

    .line 65
    :catchall_79
    move-exception v0

    move-object v2, v3

    :goto_7b
    invoke-static {v2}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V

    .line 66
    throw v0
.end method

.method static loadEntry(Ljava/lang/String;)Lio/mesalabs/unica/settings/font/FontEntry;
    .registers 6

    .line 71
    invoke-static {p0}, Lio/mesalabs/unica/settings/font/FontBank;->isSafeXmlName(Ljava/lang/String;)Z

    move-result v0

    const/4 v1, 0x0

    if-nez v0, :cond_8

    .line 72
    return-object v1

    .line 74
    :cond_8
    nop

    .line 75
    nop

    .line 77
    :try_start_a
    new-instance v0, Ljava/util/zip/ZipFile;

    const-string v2, "/system/etc/unica/font_selector/SamsungSans.apk"

    invoke-direct {v0, v2}, Ljava/util/zip/ZipFile;-><init>(Ljava/lang/String;)V
    :try_end_11
    .catch Ljava/lang/Exception; {:try_start_a .. :try_end_11} :catch_56
    .catchall {:try_start_a .. :try_end_11} :catchall_53

    .line 78
    :try_start_11
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "assets/xml/"

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v2

    invoke-virtual {v2, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    invoke-virtual {v0, p0}, Ljava/util/zip/ZipFile;->getEntry(Ljava/lang/String;)Ljava/util/zip/ZipEntry;

    move-result-object p0
    :try_end_28
    .catch Ljava/lang/Exception; {:try_start_11 .. :try_end_28} :catch_50
    .catchall {:try_start_11 .. :try_end_28} :catchall_4e

    .line 79
    if-nez p0, :cond_32

    .line 80
    nop

    .line 89
    invoke-static {v1}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V

    .line 90
    invoke-static {v0}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V

    .line 80
    return-object v1

    .line 82
    :cond_32
    :try_start_32
    invoke-virtual {v0, p0}, Ljava/util/zip/ZipFile;->getInputStream(Ljava/util/zip/ZipEntry;)Ljava/io/InputStream;

    move-result-object v2
    :try_end_36
    .catch Ljava/lang/Exception; {:try_start_32 .. :try_end_36} :catch_50
    .catchall {:try_start_32 .. :try_end_36} :catchall_4e

    .line 83
    :try_start_36
    invoke-virtual {p0}, Ljava/util/zip/ZipEntry;->getName()Ljava/lang/String;

    move-result-object p0

    invoke-static {p0, v2}, Lio/mesalabs/unica/settings/font/FontEntry;->parse(Ljava/lang/String;Ljava/io/InputStream;)Lio/mesalabs/unica/settings/font/FontEntry;

    move-result-object p0

    .line 84
    invoke-virtual {p0}, Lio/mesalabs/unica/settings/font/FontEntry;->isUsable()Z

    move-result v3
    :try_end_42
    .catch Ljava/lang/Exception; {:try_start_36 .. :try_end_42} :catch_4c
    .catchall {:try_start_36 .. :try_end_42} :catchall_68

    if-eqz v3, :cond_45

    move-object v1, p0

    .line 89
    :cond_45
    invoke-static {v2}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V

    .line 90
    invoke-static {v0}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V

    .line 84
    return-object v1

    .line 85
    :catch_4c
    move-exception p0

    goto :goto_59

    .line 89
    :catchall_4e
    move-exception p0

    goto :goto_6a

    .line 85
    :catch_50
    move-exception p0

    move-object v2, v1

    goto :goto_59

    .line 89
    :catchall_53
    move-exception p0

    move-object v0, v1

    goto :goto_6a

    .line 85
    :catch_56
    move-exception p0

    move-object v0, v1

    move-object v2, v0

    .line 86
    :goto_59
    :try_start_59
    const-string v3, "UnicaFontSelector"

    const-string v4, "Unable to load selected font"

    invoke-static {v3, v4, p0}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
    :try_end_60
    .catchall {:try_start_59 .. :try_end_60} :catchall_68

    .line 87
    nop

    .line 89
    invoke-static {v2}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V

    .line 90
    invoke-static {v0}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V

    .line 87
    return-object v1

    .line 89
    :catchall_68
    move-exception p0

    move-object v1, v2

    :goto_6a
    invoke-static {v1}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V

    .line 90
    invoke-static {v0}, Lio/mesalabs/unica/settings/font/FontBank;->closeQuietly(Ljava/lang/Object;)V

    .line 91
    throw p0
.end method

.method static setSelectedXmls(Landroid/content/Context;Ljava/util/Set;)Z
    .registers 3
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Landroid/content/Context;",
            "Ljava/util/Set<",
            "Ljava/lang/String;",
            ">;)Z"
        }
    .end annotation

    .line 118
    if-nez p0, :cond_4

    .line 119
    const/4 p0, 0x0

    return p0

    .line 121
    :cond_4
    invoke-virtual {p0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object p0

    const-string v0, "unica_font_selector_selected_xmls"

    invoke-static {p1}, Lio/mesalabs/unica/settings/font/FontBank;->encodeSelectedXmls(Ljava/util/Set;)Ljava/lang/String;

    move-result-object p1

    invoke-static {p0, v0, p1}, Landroid/provider/Settings$Global;->putString(Landroid/content/ContentResolver;Ljava/lang/String;Ljava/lang/String;)Z

    move-result p0

    return p0
.end method

.method static toVirtualPackage(Ljava/lang/String;)Ljava/lang/String;
    .registers 3

    .line 148
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "unica_font:"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {v0, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    return-object p0
.end method

.method static xmlFromPackageOrFile(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
    .registers 3

    .line 156
    invoke-static {p1}, Lio/mesalabs/unica/settings/font/FontBank;->isUnicaFontPackage(Ljava/lang/String;)Z

    move-result v0

    if-eqz v0, :cond_11

    .line 157
    const-string p0, "unica_font:"

    invoke-virtual {p0}, Ljava/lang/String;->length()I

    move-result p0

    invoke-virtual {p1, p0}, Ljava/lang/String;->substring(I)Ljava/lang/String;

    move-result-object p0

    return-object p0

    .line 159
    :cond_11
    return-object p0
.end method
