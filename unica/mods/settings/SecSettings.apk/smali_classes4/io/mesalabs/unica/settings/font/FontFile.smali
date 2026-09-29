.class final Lio/mesalabs/unica/settings/font/FontFile;
.super Ljava/lang/Object;
.source "FontFile.java"


# instance fields
.field final droidName:Ljava/lang/String;

.field final fileName:Ljava/lang/String;


# direct methods
.method constructor <init>(Ljava/lang/String;Ljava/lang/String;)V
    .registers 3

    .line 7
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    .line 8
    iput-object p1, p0, Lio/mesalabs/unica/settings/font/FontFile;->fileName:Ljava/lang/String;

    .line 9
    iput-object p2, p0, Lio/mesalabs/unica/settings/font/FontFile;->droidName:Ljava/lang/String;

    .line 10
    return-void
.end method
