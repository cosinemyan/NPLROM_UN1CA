.class public final Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ToggleChangeListener;
.super Ljava/lang/Object;
.source "CameraFeatureFragment.java"

# interfaces
.implements Landroidx/preference/Preference$OnPreferenceChangeListener;


# instance fields
.field public final mAttribute:Ljava/lang/String;

.field public final mContext:Landroid/content/Context;

.field public final mFragment:Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;

.field public final mName:Ljava/lang/String;


# direct methods
.method public constructor <init>(Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput-object p1, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ToggleChangeListener;->mFragment:Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;

    iput-object p2, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ToggleChangeListener;->mContext:Landroid/content/Context;

    iput-object p3, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ToggleChangeListener;->mName:Ljava/lang/String;

    iput-object p4, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ToggleChangeListener;->mAttribute:Ljava/lang/String;

    return-void
.end method


# virtual methods
.method public final onPreferenceChange(Landroidx/preference/Preference;Ljava/lang/Object;)Z
    .locals 4

    check-cast p2, Ljava/lang/Boolean;

    invoke-virtual {p2}, Ljava/lang/Boolean;->booleanValue()Z

    move-result p2

    invoke-static {p2}, Ljava/lang/Boolean;->toString(Z)Ljava/lang/String;

    move-result-object p2

    iget-object v0, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ToggleChangeListener;->mContext:Landroid/content/Context;

    iget-object v1, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ToggleChangeListener;->mName:Ljava/lang/String;

    iget-object v2, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ToggleChangeListener;->mAttribute:Ljava/lang/String;

    invoke-static {v0, v1, v2, p2}, Lio/mesalabs/unica/settings/spoof/CameraFeatureUtils;->setFeatureValue(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z

    move-result v0

    iget-object v1, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ToggleChangeListener;->mFragment:Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;

    if-eqz v0, :cond_0

    iget-object p0, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ToggleChangeListener;->mAttribute:Ljava/lang/String;

    invoke-static {p0, p2}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->buildSummary(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object p0

    invoke-virtual {p1, p0}, Landroidx/preference/Preference;->setSummary(Ljava/lang/CharSequence;)V

    const-string p0, "Camera feature saved. Samsung Camera was reloaded."

    invoke-virtual {v1, p0}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->showToast(Ljava/lang/String;)V

    const/4 p0, 0x1

    return p0

    :cond_0
    const-string p0, "Could not save camera feature value."

    invoke-virtual {v1, p0}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->showToast(Ljava/lang/String;)V

    const/4 p0, 0x0

    return p0
.end method
