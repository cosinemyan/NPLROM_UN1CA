.class final Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;
.super Ljava/lang/Object;
.source "ScpmAllowlistFragment.java"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x1a
    name = "AppEntry"
.end annotation


# instance fields
.field final label:Ljava/lang/String;

.field final packageName:Ljava/lang/String;


# direct methods
.method constructor <init>(Ljava/lang/String;Ljava/lang/String;)V
    .registers 3
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x0,
            0x0
        }
        names = {
            "packageName",
            "label"
        }
    .end annotation

    .line 392
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    .line 393
    iput-object p1, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;->packageName:Ljava/lang/String;

    .line 394
    iput-object p2, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;->label:Ljava/lang/String;

    .line 395
    return-void
.end method
