.class public final Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ValueDialogClickListener;
.super Ljava/lang/Object;
.source "CameraFeatureFragment.java"

# interfaces
.implements Landroid/content/DialogInterface$OnClickListener;


# instance fields
.field public final mAttribute:Ljava/lang/String;

.field public final mEditText:Landroid/widget/EditText;

.field public final mFragment:Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;

.field public final mName:Ljava/lang/String;


# direct methods
.method public constructor <init>(Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;Ljava/lang/String;Ljava/lang/String;Landroid/widget/EditText;)V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput-object p1, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ValueDialogClickListener;->mFragment:Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;

    iput-object p2, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ValueDialogClickListener;->mName:Ljava/lang/String;

    iput-object p3, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ValueDialogClickListener;->mAttribute:Ljava/lang/String;

    iput-object p4, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ValueDialogClickListener;->mEditText:Landroid/widget/EditText;

    return-void
.end method


# virtual methods
.method public final onClick(Landroid/content/DialogInterface;I)V
    .locals 5

    iget-object p1, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ValueDialogClickListener;->mEditText:Landroid/widget/EditText;

    invoke-virtual {p1}, Landroid/widget/EditText;->getText()Landroid/text/Editable;

    move-result-object p1

    invoke-virtual {p1}, Ljava/lang/Object;->toString()Ljava/lang/String;

    move-result-object p1

    iget-object p2, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ValueDialogClickListener;->mFragment:Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;

    invoke-virtual {p2}, Lcom/android/settings/core/InstrumentedPreferenceFragment;->getPrefContext()Landroid/content/Context;

    move-result-object v0

    iget-object v1, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ValueDialogClickListener;->mName:Ljava/lang/String;

    iget-object p0, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ValueDialogClickListener;->mAttribute:Ljava/lang/String;

    invoke-static {v0, v1, p0, p1}, Lio/mesalabs/unica/settings/spoof/CameraFeatureUtils;->setFeatureValue(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z

    move-result p0

    if-eqz p0, :cond_0

    const-string p0, "Camera feature saved. Samsung Camera was reloaded."

    invoke-virtual {p2, p0}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->showToast(Ljava/lang/String;)V

    invoke-virtual {p2}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->rebuildFeaturePreferences()V

    return-void

    :cond_0
    const-string p0, "Could not save camera feature value."

    invoke-virtual {p2, p0}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->showToast(Ljava/lang/String;)V

    return-void
.end method
