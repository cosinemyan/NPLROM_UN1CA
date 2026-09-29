.class final Lio/mesalabs/unica/settings/font/FontEntry;
.super Ljava/lang/Object;
.source "FontEntry.java"


# instance fields
.field final directoryName:Ljava/lang/String;

.field final displayName:Ljava/lang/String;

.field final files:Ljava/util/ArrayList;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/ArrayList<",
            "Lio/mesalabs/unica/settings/font/FontFile;",
            ">;"
        }
    .end annotation
.end field

.field final previewFileName:Ljava/lang/String;

.field final safeName:Ljava/lang/String;

.field final xmlName:Ljava/lang/String;


# direct methods
.method private constructor <init>(Ljava/lang/String;Ljava/lang/String;Ljava/util/ArrayList;)V
    .registers 5
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/lang/String;",
            "Ljava/lang/String;",
            "Ljava/util/ArrayList<",
            "Lio/mesalabs/unica/settings/font/FontFile;",
            ">;)V"
        }
    .end annotation

    .line 15
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    .line 16
    iput-object p1, p0, Lio/mesalabs/unica/settings/font/FontEntry;->xmlName:Ljava/lang/String;

    .line 17
    if-eqz p2, :cond_e

    invoke-virtual {p2}, Ljava/lang/String;->length()I

    move-result v0

    if-lez v0, :cond_e

    goto :goto_12

    :cond_e
    invoke-static {p1}, Lio/mesalabs/unica/settings/font/FontEntry;->cleanName(Ljava/lang/String;)Ljava/lang/String;

    move-result-object p2

    :goto_12
    iput-object p2, p0, Lio/mesalabs/unica/settings/font/FontEntry;->displayName:Ljava/lang/String;

    .line 18
    iput-object p3, p0, Lio/mesalabs/unica/settings/font/FontEntry;->files:Ljava/util/ArrayList;

    .line 19
    invoke-static {p1}, Lio/mesalabs/unica/settings/font/FontEntry;->removeXmlSuffix(Ljava/lang/String;)Ljava/lang/String;

    move-result-object p2

    invoke-static {p2}, Lio/mesalabs/unica/settings/font/FontEntry;->sanitize(Ljava/lang/String;)Ljava/lang/String;

    move-result-object p2

    iput-object p2, p0, Lio/mesalabs/unica/settings/font/FontEntry;->directoryName:Ljava/lang/String;

    .line 20
    invoke-static {p1}, Lio/mesalabs/unica/settings/font/FontEntry;->sanitize(Ljava/lang/String;)Ljava/lang/String;

    move-result-object p1

    iput-object p1, p0, Lio/mesalabs/unica/settings/font/FontEntry;->safeName:Ljava/lang/String;

    .line 21
    invoke-virtual {p3}, Ljava/util/ArrayList;->isEmpty()Z

    move-result p1

    if-eqz p1, :cond_2e

    const/4 p1, 0x0

    goto :goto_37

    :cond_2e
    const/4 p1, 0x0

    invoke-virtual {p3, p1}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Lio/mesalabs/unica/settings/font/FontFile;

    iget-object p1, p1, Lio/mesalabs/unica/settings/font/FontFile;->fileName:Ljava/lang/String;

    :goto_37
    iput-object p1, p0, Lio/mesalabs/unica/settings/font/FontEntry;->previewFileName:Ljava/lang/String;

    .line 22
    return-void
.end method

.method static cleanName(Ljava/lang/String;)Ljava/lang/String;
    .registers 3

    .line 35
    invoke-static {p0}, Lio/mesalabs/unica/settings/font/FontEntry;->removeXmlSuffix(Ljava/lang/String;)Ljava/lang/String;

    move-result-object p0

    const/16 v0, 0x5f

    const/16 v1, 0x20

    invoke-virtual {p0, v0, v1}, Ljava/lang/String;->replace(CC)Ljava/lang/String;

    move-result-object p0

    return-object p0
.end method

.method static parse(Ljava/lang/String;Ljava/io/InputStream;)Lio/mesalabs/unica/settings/font/FontEntry;
    .registers 5
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/lang/Exception;
        }
    .end annotation

    .line 25
    const/16 v0, 0x2f

    invoke-virtual {p0, v0}, Ljava/lang/String;->lastIndexOf(I)I

    move-result v0

    .line 26
    add-int/lit8 v0, v0, 0x1

    invoke-virtual {p0, v0}, Ljava/lang/String;->substring(I)Ljava/lang/String;

    move-result-object p0

    .line 27
    new-instance v0, Lio/mesalabs/unica/settings/font/FontXmlHandler;

    invoke-direct {v0}, Lio/mesalabs/unica/settings/font/FontXmlHandler;-><init>()V

    .line 28
    invoke-static {}, Ljavax/xml/parsers/SAXParserFactory;->newInstance()Ljavax/xml/parsers/SAXParserFactory;

    move-result-object v1

    .line 29
    const/4 v2, 0x0

    invoke-virtual {v1, v2}, Ljavax/xml/parsers/SAXParserFactory;->setNamespaceAware(Z)V

    .line 30
    invoke-virtual {v1}, Ljavax/xml/parsers/SAXParserFactory;->newSAXParser()Ljavax/xml/parsers/SAXParser;

    move-result-object v1

    invoke-virtual {v1, p1, v0}, Ljavax/xml/parsers/SAXParser;->parse(Ljava/io/InputStream;Lorg/xml/sax/helpers/DefaultHandler;)V

    .line 31
    new-instance p1, Lio/mesalabs/unica/settings/font/FontEntry;

    iget-object v1, v0, Lio/mesalabs/unica/settings/font/FontXmlHandler;->displayName:Ljava/lang/String;

    iget-object v0, v0, Lio/mesalabs/unica/settings/font/FontXmlHandler;->files:Ljava/util/ArrayList;

    invoke-direct {p1, p0, v1, v0}, Lio/mesalabs/unica/settings/font/FontEntry;-><init>(Ljava/lang/String;Ljava/lang/String;Ljava/util/ArrayList;)V

    return-object p1
.end method

.method static removeXmlSuffix(Ljava/lang/String;)Ljava/lang/String;
    .registers 3

    .line 39
    if-eqz p0, :cond_16

    const-string v0, ".xml"

    invoke-virtual {p0, v0}, Ljava/lang/String;->endsWith(Ljava/lang/String;)Z

    move-result v0

    if-eqz v0, :cond_16

    .line 40
    invoke-virtual {p0}, Ljava/lang/String;->length()I

    move-result v0

    add-int/lit8 v0, v0, -0x4

    const/4 v1, 0x0

    invoke-virtual {p0, v1, v0}, Ljava/lang/String;->substring(II)Ljava/lang/String;

    move-result-object p0

    return-object p0

    .line 42
    :cond_16
    return-object p0
.end method

.method static sanitize(Ljava/lang/String;)Ljava/lang/String;
    .registers 7

    .line 46
    const-string v0, "unica_font"

    if-nez p0, :cond_5

    .line 47
    return-object v0

    .line 49
    :cond_5
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-virtual {p0}, Ljava/lang/String;->length()I

    move-result v2

    invoke-direct {v1, v2}, Ljava/lang/StringBuilder;-><init>(I)V

    .line 50
    const/4 v2, 0x0

    :goto_f
    invoke-virtual {p0}, Ljava/lang/String;->length()I

    move-result v3

    if-ge v2, v3, :cond_48

    .line 51
    invoke-virtual {p0, v2}, Ljava/lang/String;->charAt(I)C

    move-result v3

    .line 52
    const/16 v4, 0x61

    if-lt v3, v4, :cond_21

    const/16 v4, 0x7a

    if-le v3, v4, :cond_42

    :cond_21
    const/16 v4, 0x41

    if-lt v3, v4, :cond_29

    const/16 v4, 0x5a

    if-le v3, v4, :cond_42

    :cond_29
    const/16 v4, 0x30

    if-lt v3, v4, :cond_31

    const/16 v4, 0x39

    if-le v3, v4, :cond_42

    :cond_31
    const/16 v4, 0x2e

    if-eq v3, v4, :cond_42

    const/16 v4, 0x5f

    if-eq v3, v4, :cond_42

    const/16 v5, 0x2d

    if-ne v3, v5, :cond_3e

    goto :goto_42

    .line 56
    :cond_3e
    invoke-virtual {v1, v4}, Ljava/lang/StringBuilder;->append(C)Ljava/lang/StringBuilder;

    goto :goto_45

    .line 54
    :cond_42
    :goto_42
    invoke-virtual {v1, v3}, Ljava/lang/StringBuilder;->append(C)Ljava/lang/StringBuilder;

    .line 50
    :goto_45
    add-int/lit8 v2, v2, 0x1

    goto :goto_f

    .line 59
    :cond_48
    invoke-virtual {v1}, Ljava/lang/StringBuilder;->length()I

    move-result p0

    if-nez p0, :cond_4f

    goto :goto_53

    :cond_4f
    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    :goto_53
    return-object v0
.end method


# virtual methods
.method isUsable()Z
    .registers 2

    .line 63
    iget-object v0, p0, Lio/mesalabs/unica/settings/font/FontEntry;->previewFileName:Ljava/lang/String;

    if-eqz v0, :cond_e

    iget-object v0, p0, Lio/mesalabs/unica/settings/font/FontEntry;->files:Ljava/util/ArrayList;

    invoke-virtual {v0}, Ljava/util/ArrayList;->size()I

    move-result v0

    if-lez v0, :cond_e

    const/4 v0, 0x1

    goto :goto_f

    :cond_e
    const/4 v0, 0x0

    :goto_f
    return v0
.end method

.method matches(Ljava/lang/String;)Z
    .registers 4

    .line 67
    const/4 v0, 0x1

    if-eqz p1, :cond_29

    invoke-virtual {p1}, Ljava/lang/String;->length()I

    move-result v1

    if-nez v1, :cond_a

    goto :goto_29

    .line 70
    :cond_a
    invoke-virtual {p1}, Ljava/lang/String;->toLowerCase()Ljava/lang/String;

    move-result-object p1

    .line 71
    iget-object v1, p0, Lio/mesalabs/unica/settings/font/FontEntry;->displayName:Ljava/lang/String;

    invoke-virtual {v1}, Ljava/lang/String;->toLowerCase()Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v1, p1}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z

    move-result v1

    if-nez v1, :cond_28

    iget-object v1, p0, Lio/mesalabs/unica/settings/font/FontEntry;->xmlName:Ljava/lang/String;

    invoke-virtual {v1}, Ljava/lang/String;->toLowerCase()Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v1, p1}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z

    move-result p1

    if-eqz p1, :cond_27

    goto :goto_28

    :cond_27
    const/4 v0, 0x0

    :cond_28
    :goto_28
    return v0

    .line 68
    :cond_29
    :goto_29
    return v0
.end method
