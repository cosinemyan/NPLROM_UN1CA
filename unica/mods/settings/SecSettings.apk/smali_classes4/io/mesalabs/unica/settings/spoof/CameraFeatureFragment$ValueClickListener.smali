.class public final Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ValueClickListener;
.super Ljava/lang/Object;
.source "CameraFeatureFragment.java"

# interfaces
.implements Landroidx/preference/Preference$OnPreferenceClickListener;


# instance fields
.field public final mAttribute:Ljava/lang/String;

.field public final mFragment:Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;

.field public final mName:Ljava/lang/String;

.field public final mValue:Ljava/lang/String;


# direct methods
.method public constructor <init>(Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput-object p1, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ValueClickListener;->mFragment:Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;

    iput-object p2, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ValueClickListener;->mName:Ljava/lang/String;

    iput-object p3, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ValueClickListener;->mAttribute:Ljava/lang/String;

    iput-object p4, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ValueClickListener;->mValue:Ljava/lang/String;

    return-void
.end method


# virtual methods
.method public final onPreferenceClick(Landroidx/preference/Preference;)Z
    .locals 3

    iget-object p1, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ValueClickListener;->mFragment:Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;

    iget-object v0, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ValueClickListener;->mName:Ljava/lang/String;

    iget-object v1, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ValueClickListener;->mAttribute:Ljava/lang/String;

    iget-object p0, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$ValueClickListener;->mValue:Ljava/lang/String;

    invoke-virtual {p1, v0, v1, p0}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->showValueDialog(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V

    const/4 p0, 0x1

    return p0
.end method
