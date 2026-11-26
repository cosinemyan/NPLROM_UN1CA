.class public final Lio/mesalabs/unica/localsummary/LocalSummaryProvider;
.super Landroid/content/ContentProvider;

.field private static final MAX_INPUT_CHARS:I = 0x1000

.field private static final MAX_OUTPUT_CHARS:I = 0x200

.field private static final SYSTEM_UID:I = 0x3e8


.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Landroid/content/ContentProvider;-><init>()V

    return-void
.end method

.method private static error(Ljava/lang/String;)Landroid/os/Bundle;
    .locals 2

    new-instance v0, Landroid/os/Bundle;

    invoke-direct {v0}, Landroid/os/Bundle;-><init>()V

    const-string v1, "error"

    invoke-virtual {v0, v1, p0}, Landroid/os/Bundle;->putString(Ljava/lang/String;Ljava/lang/String;)V

    return-object v0
.end method

.method private static success(Ljava/lang/String;)Landroid/os/Bundle;
    .locals 2

    new-instance v0, Landroid/os/Bundle;

    invoke-direct {v0}, Landroid/os/Bundle;-><init>()V

    const-string v1, "summary"

    invoke-virtual {v0, v1, p0}, Landroid/os/Bundle;->putString(Ljava/lang/String;Ljava/lang/String;)V

    return-object v0
.end method

.method private static readStream(Ljava/io/InputStream;)Ljava/lang/String;
    .locals 5

    new-instance v0, Ljava/io/ByteArrayOutputStream;

    invoke-direct {v0}, Ljava/io/ByteArrayOutputStream;-><init>()V

    const/16 v1, 0x400

    new-array v1, v1, [B

    :goto_read
    invoke-virtual {p0, v1}, Ljava/io/InputStream;->read([B)I

    move-result v2

    if-lez v2, :cond_done

    const/4 v3, 0x0

    invoke-virtual {v0, v1, v3, v2}, Ljava/io/ByteArrayOutputStream;->write([BII)V

    goto :goto_read

    :cond_done
    invoke-virtual {p0}, Ljava/io/InputStream;->close()V

    const-string v4, "UTF-8"

    invoke-virtual {v0, v4}, Ljava/io/ByteArrayOutputStream;->toString(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v4

    return-object v4
.end method

.method private static summarizeWithServer(Ljava/lang/String;)Ljava/lang/String;
    .locals 11

    const/4 v0, 0x0

    :try_start_0
    new-instance v1, Ljava/net/URL;

    const-string v2, "http://127.0.0.1:8085/completion"

    invoke-direct {v1, v2}, Ljava/net/URL;-><init>(Ljava/lang/String;)V

    invoke-virtual {v1}, Ljava/net/URL;->openConnection()Ljava/net/URLConnection;

    move-result-object v1

    check-cast v1, Ljava/net/HttpURLConnection;

    const-string v2, "POST"

    invoke-virtual {v1, v2}, Ljava/net/HttpURLConnection;->setRequestMethod(Ljava/lang/String;)V

    const/16 v2, 0x5dc

    invoke-virtual {v1, v2}, Ljava/net/HttpURLConnection;->setConnectTimeout(I)V

    const/16 v2, 0x2ee0

    invoke-virtual {v1, v2}, Ljava/net/HttpURLConnection;->setReadTimeout(I)V

    const/4 v2, 0x1

    invoke-virtual {v1, v2}, Ljava/net/HttpURLConnection;->setDoOutput(Z)V

    const-string v2, "Content-Type"

    const-string v3, "application/json"

    invoke-virtual {v1, v2, v3}, Ljava/net/HttpURLConnection;->setRequestProperty(Ljava/lang/String;Ljava/lang/String;)V

    new-instance v2, Ljava/lang/StringBuilder;

    const-string v3, "Summarize this notification thread in one short sentence under 18 words. Return only the summary.\n\n"

    invoke-direct {v2, v3}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    invoke-virtual {v2, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "{\"prompt\":"

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v2

    invoke-static {p0}, Lorg/json/JSONObject;->quote(Ljava/lang/String;)Ljava/lang/String;

    move-result-object p0

    invoke-virtual {v2, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    const-string v2, ",\"n_predict\":48,\"temperature\":0.1,\"stop\":[\"<|im_end|>\",\"\\n\\n\"]}"

    invoke-virtual {p0, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    const-string v2, "UTF-8"

    invoke-virtual {p0, v2}, Ljava/lang/String;->getBytes(Ljava/lang/String;)[B

    move-result-object p0

    invoke-virtual {v1}, Ljava/net/HttpURLConnection;->getOutputStream()Ljava/io/OutputStream;

    move-result-object v2

    invoke-virtual {v2, p0}, Ljava/io/OutputStream;->write([B)V

    invoke-virtual {v2}, Ljava/io/OutputStream;->close()V

    invoke-virtual {v1}, Ljava/net/HttpURLConnection;->getResponseCode()I

    move-result p0

    const/16 v2, 0xc8

    if-ne p0, v2, :cond_http_failed

    invoke-virtual {v1}, Ljava/net/HttpURLConnection;->getInputStream()Ljava/io/InputStream;

    move-result-object p0

    invoke-static {p0}, Lio/mesalabs/unica/localsummary/LocalSummaryProvider;->readStream(Ljava/io/InputStream;)Ljava/lang/String;

    move-result-object p0

    invoke-virtual {v1}, Ljava/net/HttpURLConnection;->disconnect()V

    new-instance v1, Lorg/json/JSONObject;

    invoke-direct {v1, p0}, Lorg/json/JSONObject;-><init>(Ljava/lang/String;)V

    const-string p0, "content"

    invoke-virtual {v1, p0}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    move-result-object p0

    if-eqz p0, :cond_empty

    invoke-virtual {p0}, Ljava/lang/String;->trim()Ljava/lang/String;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/String;->length()I

    move-result v1

    if-lez v1, :cond_empty

    const/16 v2, 0x200

    if-le v1, v2, :cond_success

    const/4 v1, 0x0

    invoke-virtual {p0, v1, v2}, Ljava/lang/String;->substring(II)Ljava/lang/String;

    move-result-object p0

    :cond_success
    return-object p0

    :cond_http_failed
    invoke-virtual {v1}, Ljava/net/HttpURLConnection;->disconnect()V

    :cond_empty
    return-object v0
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    :catch_0
    move-exception p0

    return-object v0
.end method

.method public call(Ljava/lang/String;Ljava/lang/String;Landroid/os/Bundle;)Landroid/os/Bundle;
    .locals 4

    invoke-static {}, Landroid/os/Binder;->getCallingUid()I

    move-result v0

    const/16 v1, 0x3e8

    if-ne v0, v1, :cond_check_permission

    goto :cond_caller_allowed

    :cond_check_permission
    invoke-virtual {p0}, Lio/mesalabs/unica/localsummary/LocalSummaryProvider;->getContext()Landroid/content/Context;

    move-result-object v0

    if-eqz v0, :cond_caller_denied

    const-string v1, "io.mesalabs.unica.permission.LOCAL_SUMMARY"

    invoke-virtual {v0, v1}, Landroid/content/Context;->checkCallingOrSelfPermission(Ljava/lang/String;)I

    move-result v0

    if-nez v0, :cond_caller_denied

    :cond_caller_allowed
    const-string v0, "summarize"

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_invalid_request

    if-eqz p3, :cond_invalid_request

    const-string v0, "input"

    invoke-virtual {p3, v0}, Landroid/os/Bundle;->getString(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    if-eqz v1, :cond_invalid_request

    invoke-virtual {v1}, Ljava/lang/String;->length()I

    move-result v0

    if-lez v0, :cond_invalid_request

    const/16 v2, 0x1000

    if-le v0, v2, :cond_start

    :cond_invalid_request
    const-string v0, "invalid request"

    invoke-static {v0}, Lio/mesalabs/unica/localsummary/LocalSummaryProvider;->error(Ljava/lang/String;)Landroid/os/Bundle;

    move-result-object v0

    return-object v0

    :cond_caller_denied
    const-string v0, "caller denied"

    invoke-static {v0}, Lio/mesalabs/unica/localsummary/LocalSummaryProvider;->error(Ljava/lang/String;)Landroid/os/Bundle;

    move-result-object v0

    return-object v0

    :cond_start
    invoke-static {v1}, Lio/mesalabs/unica/localsummary/LocalSummaryProvider;->summarizeWithServer(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    if-eqz v0, :cond_failed

    invoke-virtual {v0}, Ljava/lang/String;->length()I

    move-result v1

    if-lez v1, :cond_failed

    invoke-static {v0}, Lio/mesalabs/unica/localsummary/LocalSummaryProvider;->success(Ljava/lang/String;)Landroid/os/Bundle;

    move-result-object v0

    return-object v0

    :cond_failed
    const-string v0, "New messages are waiting."

    invoke-static {v0}, Lio/mesalabs/unica/localsummary/LocalSummaryProvider;->success(Ljava/lang/String;)Landroid/os/Bundle;

    move-result-object v0

    return-object v0
.end method

.method public delete(Landroid/net/Uri;Ljava/lang/String;[Ljava/lang/String;)I
    .locals 1

    const/4 v0, 0x0

    return v0
.end method

.method public getType(Landroid/net/Uri;)Ljava/lang/String;
    .locals 1

    const/4 v0, 0x0

    return-object v0
.end method

.method public insert(Landroid/net/Uri;Landroid/content/ContentValues;)Landroid/net/Uri;
    .locals 1

    const/4 v0, 0x0

    return-object v0
.end method

.method public onCreate()Z
    .locals 4

    :try_start_0
    const/16 v0, 0xf

    new-array v1, v0, [Ljava/lang/String;

    const/4 v0, 0x0

    const-string v2, "/system/bin/unica-summary-llama-server"

    aput-object v2, v1, v0

    const/4 v0, 0x1

    const-string v2, "-m"

    aput-object v2, v1, v0

    const/4 v0, 0x2

    const-string v2, "/system/etc/unica/local-summary/qwen2.5-0.5b-instruct-q4_k_m.gguf"

    aput-object v2, v1, v0

    const/4 v0, 0x3

    const-string v2, "--host"

    aput-object v2, v1, v0

    const/4 v0, 0x4

    const-string v2, "127.0.0.1"

    aput-object v2, v1, v0

    const/4 v0, 0x5

    const-string v2, "--port"

    aput-object v2, v1, v0

    const/4 v0, 0x6

    const-string v2, "8085"

    aput-object v2, v1, v0

    const/4 v0, 0x7

    const-string v2, "-c"

    aput-object v2, v1, v0

    const/16 v0, 0x8

    const-string v2, "1024"

    aput-object v2, v1, v0

    const/16 v0, 0x9

    const-string v2, "-t"

    aput-object v2, v1, v0

    const/16 v0, 0xa

    const-string v2, "6"

    aput-object v2, v1, v0

    const/16 v0, 0xb

    const-string v2, "-np"

    aput-object v2, v1, v0

    const/16 v0, 0xc

    const-string v2, "1"

    aput-object v2, v1, v0

    const/16 v0, 0xd

    const-string v2, "--no-webui"

    aput-object v2, v1, v0

    const/16 v0, 0xe

    const-string v2, "--no-warmup"

    aput-object v2, v1, v0

    new-instance v0, Ljava/lang/ProcessBuilder;

    invoke-direct {v0, v1}, Ljava/lang/ProcessBuilder;-><init>([Ljava/lang/String;)V

    const/4 v1, 0x1

    invoke-virtual {v0, v1}, Ljava/lang/ProcessBuilder;->redirectErrorStream(Z)Ljava/lang/ProcessBuilder;

    invoke-virtual {v0}, Ljava/lang/ProcessBuilder;->start()Ljava/lang/Process;
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    const/4 v0, 0x1

    return v0

    :catch_0
    move-exception v0

    const/4 v0, 0x1

    return v0
.end method

.method public query(Landroid/net/Uri;[Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;)Landroid/database/Cursor;
    .locals 1

    const/4 v0, 0x0

    return-object v0
.end method

.method public update(Landroid/net/Uri;Landroid/content/ContentValues;Ljava/lang/String;[Ljava/lang/String;)I
    .locals 1

    const/4 v0, 0x0

    return v0
.end method
