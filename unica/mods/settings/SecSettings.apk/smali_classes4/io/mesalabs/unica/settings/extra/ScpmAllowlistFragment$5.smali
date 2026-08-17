.class Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$5;
.super Ljava/lang/Object;
.source "ScpmAllowlistFragment.java"

# interfaces
.implements Ljava/util/Comparator;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->getCandidateApps()Ljava/util/List;
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

    .line 262
    iput-object p1, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$5;->this$0:Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public compare(Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;)I
    .registers 3
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

    .line 264
    iget-object p1, p1, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;->label:Ljava/lang/String;

    iget-object p2, p2, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;->label:Ljava/lang/String;

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

    .line 262
    check-cast p1, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;

    check-cast p2, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;

    invoke-virtual {p0, p1, p2}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$5;->compare(Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;)I

    move-result p1

    return p1
.end method
