.class public final Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ImportCallback;
.super Ljava/lang/Object;
.source "CameraFeatureFragment.java"

# interfaces
.implements Landroidx/activity/result/ActivityResultCallback;


# instance fields
.field public final mFragment:Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;


# direct methods
.method public constructor <init>(Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;)V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput-object p1, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ImportCallback;->mFragment:Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;

    return-void
.end method


# virtual methods
.method public final onActivityResult(Ljava/lang/Object;)V
    .locals 4

    check-cast p1, Landroidx/activity/result/ActivityResult;

    iget v0, p1, Landroidx/activity/result/ActivityResult;->mResultCode:I

    const/4 v1, -0x1

    if-ne v0, v1, :cond_2

    iget-object v0, p1, Landroidx/activity/result/ActivityResult;->mData:Landroid/content/Intent;

    if-eqz v0, :cond_2

    iget-object p1, p1, Landroidx/activity/result/ActivityResult;->mData:Landroid/content/Intent;

    invoke-virtual {p1}, Landroid/content/Intent;->getData()Landroid/net/Uri;

    move-result-object p1

    if-eqz p1, :cond_2

    iget-object p0, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ImportCallback;->mFragment:Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;

    invoke-virtual {p0}, Lcom/android/settings/core/InstrumentedPreferenceFragment;->getPrefContext()Landroid/content/Context;

    move-result-object v0

    invoke-static {v0, p1}, Lio/mesalabs/unica/settings/spoof/CameraFeatureUtils;->importXml(Landroid/content/Context;Landroid/net/Uri;)Z

    move-result p1

    if-eqz p1, :cond_0

    const-string p1, "Camera feature XML imported. Open Samsung Camera to load it."

    invoke-virtual {p0, p1}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->showToast(Ljava/lang/String;)V

    invoke-virtual {p0}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->rebuildFeaturePreferences()V

    goto :goto_0

    :cond_0
    const-string p1, "Could not import camera-feature.xml."

    invoke-virtual {p0, p1}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->showToast(Ljava/lang/String;)V

    :goto_0
    :cond_2
    return-void
.end method
