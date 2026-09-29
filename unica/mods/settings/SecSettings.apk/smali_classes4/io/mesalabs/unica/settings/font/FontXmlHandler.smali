.class final Lio/mesalabs/unica/settings/font/FontXmlHandler;
.super Lorg/xml/sax/helpers/DefaultHandler;
.source "FontXmlHandler.java"


# instance fields
.field displayName:Ljava/lang/String;

.field final files:Ljava/util/ArrayList;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/ArrayList<",
            "Lio/mesalabs/unica/settings/font/FontFile;",
            ">;"
        }
    .end annotation
.end field

.field private mDroidName:Ljava/lang/String;

.field private mFileName:Ljava/lang/String;

.field private mTag:Ljava/lang/String;

.field private mText:Ljava/lang/StringBuilder;


# direct methods
.method constructor <init>()V
    .registers 2

    .line 8
    invoke-direct {p0}, Lorg/xml/sax/helpers/DefaultHandler;-><init>()V

    .line 10
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    iput-object v0, p0, Lio/mesalabs/unica/settings/font/FontXmlHandler;->files:Ljava/util/ArrayList;

    return-void
.end method

.method private static elementName(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
    .registers 3

    .line 17
    if-eqz p1, :cond_9

    invoke-virtual {p1}, Ljava/lang/String;->length()I

    move-result v0

    if-lez v0, :cond_9

    move-object p0, p1

    :cond_9
    return-object p0
.end method


# virtual methods
.method public characters([CII)V
    .registers 5
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Lorg/xml/sax/SAXException;
        }
    .end annotation

    .line 36
    iget-object v0, p0, Lio/mesalabs/unica/settings/font/FontXmlHandler;->mText:Ljava/lang/StringBuilder;

    if-eqz v0, :cond_7

    .line 37
    invoke-virtual {v0, p1, p2, p3}, Ljava/lang/StringBuilder;->append([CII)Ljava/lang/StringBuilder;

    .line 39
    :cond_7
    return-void
.end method

.method public endElement(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V
    .registers 6
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Lorg/xml/sax/SAXException;
        }
    .end annotation

    .line 43
    invoke-static {p2, p3}, Lio/mesalabs/unica/settings/font/FontXmlHandler;->elementName(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object p1

    .line 44
    const-string p2, "filename"

    invoke-virtual {p2, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p3

    const/4 v0, 0x0

    if-eqz p3, :cond_26

    iget-object p3, p0, Lio/mesalabs/unica/settings/font/FontXmlHandler;->mTag:Ljava/lang/String;

    invoke-virtual {p2, p3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p2

    if-eqz p2, :cond_26

    .line 45
    iget-object p1, p0, Lio/mesalabs/unica/settings/font/FontXmlHandler;->mText:Ljava/lang/StringBuilder;

    invoke-virtual {p1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-virtual {p1}, Ljava/lang/String;->trim()Ljava/lang/String;

    move-result-object p1

    iput-object p1, p0, Lio/mesalabs/unica/settings/font/FontXmlHandler;->mFileName:Ljava/lang/String;

    .line 46
    iput-object v0, p0, Lio/mesalabs/unica/settings/font/FontXmlHandler;->mTag:Ljava/lang/String;

    .line 47
    iput-object v0, p0, Lio/mesalabs/unica/settings/font/FontXmlHandler;->mText:Ljava/lang/StringBuilder;

    goto :goto_75

    .line 48
    :cond_26
    const-string p2, "droidname"

    invoke-virtual {p2, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p3

    if-eqz p3, :cond_47

    iget-object p3, p0, Lio/mesalabs/unica/settings/font/FontXmlHandler;->mTag:Ljava/lang/String;

    invoke-virtual {p2, p3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p2

    if-eqz p2, :cond_47

    .line 49
    iget-object p1, p0, Lio/mesalabs/unica/settings/font/FontXmlHandler;->mText:Ljava/lang/StringBuilder;

    invoke-virtual {p1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-virtual {p1}, Ljava/lang/String;->trim()Ljava/lang/String;

    move-result-object p1

    iput-object p1, p0, Lio/mesalabs/unica/settings/font/FontXmlHandler;->mDroidName:Ljava/lang/String;

    .line 50
    iput-object v0, p0, Lio/mesalabs/unica/settings/font/FontXmlHandler;->mTag:Ljava/lang/String;

    .line 51
    iput-object v0, p0, Lio/mesalabs/unica/settings/font/FontXmlHandler;->mText:Ljava/lang/StringBuilder;

    goto :goto_75

    .line 52
    :cond_47
    const-string p2, "file"

    invoke-virtual {p2, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-eqz p1, :cond_75

    .line 53
    iget-object p1, p0, Lio/mesalabs/unica/settings/font/FontXmlHandler;->mFileName:Ljava/lang/String;

    if-eqz p1, :cond_71

    invoke-virtual {p1}, Ljava/lang/String;->length()I

    move-result p1

    if-lez p1, :cond_71

    iget-object p1, p0, Lio/mesalabs/unica/settings/font/FontXmlHandler;->mDroidName:Ljava/lang/String;

    if-eqz p1, :cond_71

    invoke-virtual {p1}, Ljava/lang/String;->length()I

    move-result p1

    if-lez p1, :cond_71

    .line 54
    iget-object p1, p0, Lio/mesalabs/unica/settings/font/FontXmlHandler;->files:Ljava/util/ArrayList;

    new-instance p2, Lio/mesalabs/unica/settings/font/FontFile;

    iget-object p3, p0, Lio/mesalabs/unica/settings/font/FontXmlHandler;->mFileName:Ljava/lang/String;

    iget-object v1, p0, Lio/mesalabs/unica/settings/font/FontXmlHandler;->mDroidName:Ljava/lang/String;

    invoke-direct {p2, p3, v1}, Lio/mesalabs/unica/settings/font/FontFile;-><init>(Ljava/lang/String;Ljava/lang/String;)V

    invoke-virtual {p1, p2}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    .line 56
    :cond_71
    iput-object v0, p0, Lio/mesalabs/unica/settings/font/FontXmlHandler;->mFileName:Ljava/lang/String;

    .line 57
    iput-object v0, p0, Lio/mesalabs/unica/settings/font/FontXmlHandler;->mDroidName:Ljava/lang/String;

    .line 59
    :cond_75
    :goto_75
    return-void
.end method

.method public startElement(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lorg/xml/sax/Attributes;)V
    .registers 5
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Lorg/xml/sax/SAXException;
        }
    .end annotation

    .line 22
    invoke-static {p2, p3}, Lio/mesalabs/unica/settings/font/FontXmlHandler;->elementName(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object p1

    .line 23
    const-string p2, "font"

    invoke-virtual {p2, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p2

    if-eqz p2, :cond_15

    .line 24
    const-string p1, "displayname"

    invoke-interface {p4, p1}, Lorg/xml/sax/Attributes;->getValue(Ljava/lang/String;)Ljava/lang/String;

    move-result-object p1

    iput-object p1, p0, Lio/mesalabs/unica/settings/font/FontXmlHandler;->displayName:Ljava/lang/String;

    goto :goto_3c

    .line 25
    :cond_15
    const-string p2, "file"

    invoke-virtual {p2, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p2

    if-eqz p2, :cond_23

    .line 26
    const/4 p1, 0x0

    iput-object p1, p0, Lio/mesalabs/unica/settings/font/FontXmlHandler;->mFileName:Ljava/lang/String;

    .line 27
    iput-object p1, p0, Lio/mesalabs/unica/settings/font/FontXmlHandler;->mDroidName:Ljava/lang/String;

    goto :goto_3c

    .line 28
    :cond_23
    const-string p2, "filename"

    invoke-virtual {p2, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p2

    if-nez p2, :cond_33

    const-string p2, "droidname"

    invoke-virtual {p2, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p2

    if-eqz p2, :cond_3c

    .line 29
    :cond_33
    iput-object p1, p0, Lio/mesalabs/unica/settings/font/FontXmlHandler;->mTag:Ljava/lang/String;

    .line 30
    new-instance p1, Ljava/lang/StringBuilder;

    invoke-direct {p1}, Ljava/lang/StringBuilder;-><init>()V

    iput-object p1, p0, Lio/mesalabs/unica/settings/font/FontXmlHandler;->mText:Ljava/lang/StringBuilder;

    .line 32
    :cond_3c
    :goto_3c
    return-void
.end method
