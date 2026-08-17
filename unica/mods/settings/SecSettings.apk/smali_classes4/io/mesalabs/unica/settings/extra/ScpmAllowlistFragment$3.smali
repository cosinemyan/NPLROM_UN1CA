.class Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$3;
.super Ljava/lang/Object;
.source "ScpmAllowlistFragment.java"

# interfaces
.implements Landroid/content/DialogInterface$OnClickListener;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->showManualPackageDialog()V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic this$0:Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;

.field final synthetic val$apps:Ljava/util/List;

.field final synthetic val$checked:[Z


# direct methods
.method constructor <init>(Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;Ljava/util/List;[Z)V
    .registers 4
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x8010,
            0x1010,
            0x1010
        }
        names = {
            "this$0",
            "val$apps",
            "val$checked"
        }
    .end annotation

    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()V"
        }
    .end annotation

    .line 227
    iput-object p1, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$3;->this$0:Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;

    iput-object p2, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$3;->val$apps:Ljava/util/List;

    iput-object p3, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$3;->val$checked:[Z

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public onClick(Landroid/content/DialogInterface;I)V
    .registers 5
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x0,
            0x0
        }
        names = {
            "dialog",
            "which"
        }
    .end annotation

    .line 229
    new-instance p1, Ljava/util/LinkedHashSet;

    invoke-direct {p1}, Ljava/util/LinkedHashSet;-><init>()V

    .line 230
    const/4 p2, 0x0

    move v0, p2

    :goto_7
    iget-object v1, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$3;->val$apps:Ljava/util/List;

    invoke-interface {v1}, Ljava/util/List;->size()I

    move-result v1

    if-ge v0, v1, :cond_25

    .line 231
    iget-object v1, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$3;->val$checked:[Z

    aget-boolean v1, v1, v0

    if-eqz v1, :cond_22

    .line 232
    iget-object v1, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$3;->val$apps:Ljava/util/List;

    invoke-interface {v1, v0}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;

    iget-object v1, v1, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$AppEntry;->packageName:Ljava/lang/String;

    invoke-virtual {p1, v1}, Ljava/util/LinkedHashSet;->add(Ljava/lang/Object;)Z

    .line 230
    :cond_22
    add-int/lit8 v0, v0, 0x1

    goto :goto_7

    .line 235
    :cond_25
    iget-object v0, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$3;->this$0:Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;

    # getter for: Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->mContext:Landroid/content/Context;
    invoke-static {v0}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->access$000(Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;)Landroid/content/Context;

    move-result-object v0

    invoke-virtual {v0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v0

    const-string v1, "unica_scpm_allowlist_enable_all"

    invoke-static {v0, v1, p2}, Landroid/provider/Settings$System;->putInt(Landroid/content/ContentResolver;Ljava/lang/String;I)Z

    .line 236
    iget-object p2, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$3;->this$0:Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;

    # invokes: Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->savePackages(Ljava/lang/Iterable;)V
    invoke-static {p2, p1}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->access$100(Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;Ljava/lang/Iterable;)V

    .line 237
    iget-object p1, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$3;->this$0:Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;

    # invokes: Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->writeControlFiles()V
    invoke-static {p1}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->access$200(Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;)V

    .line 238
    iget-object p1, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$3;->this$0:Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;

    # invokes: Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->refreshState()V
    invoke-static {p1}, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;->access$300(Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;)V

    .line 239
    return-void
.end method
