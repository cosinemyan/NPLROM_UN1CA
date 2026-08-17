.class public final Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$SearchDialogClickListener;
.super Ljava/lang/Object;
.source "CameraFeatureFragment.java"

# interfaces
.implements Landroid/content/DialogInterface$OnClickListener;


# instance fields
.field public final mEditText:Landroid/widget/EditText;

.field public final mFragment:Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;


# direct methods
.method public constructor <init>(Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;Landroid/widget/EditText;)V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput-object p1, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$SearchDialogClickListener;->mFragment:Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;

    iput-object p2, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$SearchDialogClickListener;->mEditText:Landroid/widget/EditText;

    return-void
.end method


# virtual methods
.method public final onClick(Landroid/content/DialogInterface;I)V
    .locals 1

    iget-object p1, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$SearchDialogClickListener;->mEditText:Landroid/widget/EditText;

    invoke-virtual {p1}, Landroid/widget/EditText;->getText()Landroid/text/Editable;

    move-result-object p1

    invoke-virtual {p1}, Ljava/lang/Object;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-virtual {p1}, Ljava/lang/String;->trim()Ljava/lang/String;

    move-result-object p1

    invoke-static {p1}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z

    move-result p2

    iget-object p0, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment$SearchDialogClickListener;->mFragment:Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;

    if-eqz p2, :cond_set_query

    const/4 p1, 0x0

    :cond_set_query
    iput-object p1, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->mSearchQuery:Ljava/lang/String;

    const/4 v0, 0x0

    iput-object v0, p0, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->mExpandedCategory:Ljava/lang/String;

    invoke-virtual {p0}, Lio/mesalabs/unica/settings/spoof/CameraFeatureFragment;->rebuildFeaturePreferences()V

    return-void
.end method
