.class Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$4;
.super Ljava/lang/Object;
.source "ScpmAllowlistFragment.java"

# interfaces
.implements Landroid/content/DialogInterface$OnMultiChoiceClickListener;


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

.field final synthetic val$checked:[Z


# direct methods
.method constructor <init>(Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;[Z)V
    .registers 3
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x8010,
            0x1010
        }
        names = {
            "this$0",
            "val$checked"
        }
    .end annotation

    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()V"
        }
    .end annotation

    .line 222
    iput-object p1, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$4;->this$0:Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment;

    iput-object p2, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$4;->val$checked:[Z

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public onClick(Landroid/content/DialogInterface;IZ)V
    .registers 4
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x0,
            0x0,
            0x0
        }
        names = {
            "dialog",
            "which",
            "isChecked"
        }
    .end annotation

    .line 224
    iget-object p1, p0, Lio/mesalabs/unica/settings/extra/ScpmAllowlistFragment$4;->val$checked:[Z

    aput-boolean p3, p1, p2

    .line 225
    return-void
.end method
