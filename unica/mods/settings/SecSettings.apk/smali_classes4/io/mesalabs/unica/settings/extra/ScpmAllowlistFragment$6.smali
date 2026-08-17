.class Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$6;
.super Ljava/lang/Object;
.source "ScpmAllowlistFragment.java"

# interfaces
.implements Ljava/util/Comparator;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getInstalledPackageApps()Ljava/util/List;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation

.annotation system Ldalvik/annotation/Signature;
    value = {
        "Ljava/lang/Object;",
        "Ljava/util/Comparator<",
        "Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;",
        ">;"
    }
.end annotation


# instance fields
.field final synthetic this$0:Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;


# direct methods
.method constructor <init>(Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;)V
    .registers 2
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x8010
        }
        names = {
            "this$0"
        }
    .end annotation

    .line 282
    iput-object p1, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$6;->this$0:Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public compare(Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;)I
    .registers 5
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x0,
            0x0
        }
        names = {
            "left",
            "right"
        }
    .end annotation

    .line 284
    iget-object v0, p1, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;->label:Ljava/lang/String;

    iget-object v1, p2, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;->label:Ljava/lang/String;

    invoke-virtual {v0, v1}, Ljava/lang/String;->compareToIgnoreCase(Ljava/lang/String;)I

    move-result v0

    .line 285
    if-eqz v0, :cond_b

    .line 286
    return v0

    .line 288
    :cond_b
    iget-object p1, p1, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;->packageName:Ljava/lang/String;

    iget-object p2, p2, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;->packageName:Ljava/lang/String;

    invoke-virtual {p1, p2}, Ljava/lang/String;->compareToIgnoreCase(Ljava/lang/String;)I

    move-result p1

    return p1
.end method

.method public bridge synthetic compare(Ljava/lang/Object;Ljava/lang/Object;)I
    .registers 3
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x1000,
            0x1000
        }
        names = {
            "left",
            "right"
        }
    .end annotation

    .line 282
    check-cast p1, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;

    check-cast p2, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;

    invoke-virtual {p0, p1, p2}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$6;->compare(Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;)I

    move-result p1

    return p1
.end method
