.class public Lio/mesalabs/unica/settings/extra/NotiAiJsonPreferenceController;
.super Lcom/android/settings/core/TogglePreferenceController;
.source "NotiAiJsonPreferenceController.java"


# static fields
.field private static final PROP:Ljava/lang/String; = "persist.sys.unica.noti_ai_json"


# direct methods
.method public constructor <init>(Landroid/content/Context;Ljava/lang/String;)V
    .locals 0

    invoke-direct {p0, p1, p2}, Lcom/android/settings/core/TogglePreferenceController;-><init>(Landroid/content/Context;Ljava/lang/String;)V

    return-void
.end method


# virtual methods
.method public getAvailabilityStatus()I
    .locals 0

    const/4 p0, 0x0

    return p0
.end method

.method public isChecked()Z
    .locals 1

    const-string p0, "persist.sys.unica.noti_ai_json"

    const/4 v0, 0x0

    invoke-static {p0, v0}, Landroid/os/SemSystemProperties;->getBoolean(Ljava/lang/String;Z)Z

    move-result p0

    return p0
.end method

.method public isControllable()Z
    .locals 0

    const/4 p0, 0x1

    return p0
.end method

.method public setChecked(Z)Z
    .locals 0

    const-string p0, "persist.sys.unica.noti_ai_json"

    invoke-static {p1}, Ljava/lang/Boolean;->toString(Z)Ljava/lang/String;

    move-result-object p1

    invoke-static {p0, p1}, Landroid/os/SemSystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V

    const/4 p0, 0x1

    return p0
.end method
