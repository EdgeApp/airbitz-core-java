#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <ABC.h>

// cached refs for later callbacks
JavaVM * g_vm;
jobject g_obj;
jmethodID g_async_callback;
void *bitcoinInfo;

void bitcoinCallback(const tABC_AsyncBitCoinInfo *pInfo) {
	JNIEnv * g_env;
	int getEnvStat = (*g_vm)->GetEnv(g_vm, (void **)&g_env, JNI_VERSION_1_6);
	if (getEnvStat == JNI_EDETACHED)
    {
		if ((*g_vm)->AttachCurrentThread(g_vm,
                    (struct JNINativeInterface const ***)&g_env, NULL) != 0)
        {
		}
	}
    (*g_env)->CallVoidMethod(g_env, g_obj, g_async_callback, (void *) pInfo);
}

void ABC_BitCoin_Event_Callback(const tABC_AsyncBitCoinInfo *pInfo)
{
    bitcoinCallback(pInfo);
}

JNIEXPORT jint JNICALL
Java_co_airbitz_internal_Jni_coreWatcherLoop(
        JNIEnv *jenv, jclass jcls, jstring juuid, jlong jerrorp) {
    jint jresult = 0 ;
    char *uuid = (char *) 0 ;
    tABC_BitCoin_Event_Callback callback = (tABC_BitCoin_Event_Callback) 0 ;
    void *bcInfo = (void *) 0 ;
    tABC_Error *errorp = (tABC_Error *) 0 ;
    tABC_CC result;

    (void)jenv;
    (void)jcls;

    uuid = 0;
    if (juuid)
    {
        uuid = (char *)(*jenv)->GetStringUTFChars(jenv, juuid, 0);
        if (!uuid)
            return 0;
    }

    callback = ABC_BitCoin_Event_Callback;
    bcInfo = *(void **)&bitcoinInfo;

    errorp = *(tABC_Error **)&jerrorp;

    result = (tABC_CC) ABC_WatcherLoop((char const *)uuid, callback, bcInfo, errorp);
    jresult = (jint) result;
    if (uuid)
        (*jenv)->ReleaseStringUTFChars(jenv, juuid, (const char *)uuid);
    return jresult;
}

JNIEXPORT jboolean JNICALL
Java_co_airbitz_core_Engine_registerAsyncCallback(JNIEnv *env, jobject obj)
{
    // convert local to global reference
    // (local will die after this method call)
    g_obj = (*env)->NewGlobalRef(env, obj);

    // Save global vm
    int status = (*env)->GetJavaVM(env, &g_vm);
    if (status != 0)
    {
        return false;
    }

    // save refs for callback
    jclass g_clazz = (*env)->GetObjectClass(env, g_obj);
    if (g_clazz == NULL)
    {
        return false;
    }

    g_async_callback = (*env)->GetMethodID(
            env, g_clazz, "callbackAsyncBitcoinInfo", "(J)V");
    if (g_async_callback == NULL) {
        return false;
    }
    return true;
}

/*
 * Return String from ptr to string
 */
JNIEXPORT jstring JNICALL
Java_co_airbitz_internal_Jni_getStringAtPtr(JNIEnv *env, jobject obj, jlong ptr)
{
    char *buf = *(char **) &ptr; //*(unsigned int **)&jarg4;
    jstring jresult = (*env)->NewStringUTF(env, buf);
    return jresult;
}

/*
 * Return byte array from ptr
 */
JNIEXPORT jbyteArray JNICALL
Java_co_airbitz_internal_Jni_getBytesAtPtr(
        JNIEnv *env, jobject obj, jlong ptr, jint len)
{
    jbyteArray result = (*env)->NewByteArray(env, len);
    (*env)->SetByteArrayRegion(env, result, 0, len, *(const jbyte **) &ptr);
    return result;
}

/*
 * Return list of all numbers of currencies at index
 */
JNIEXPORT jintArray JNICALL
Java_co_airbitz_internal_Jni_getCoreCurrencyNumbers(JNIEnv *env, jclass cls)
{
    jintArray result;

    tABC_Error error;
    int currencyCount;
    tABC_Currency *currencies;
    ABC_GetCurrencies(&currencies, &currencyCount, &error);

    result = (*env)->NewIntArray(env, currencyCount);
    if (result == NULL) {
        return NULL; /* out of memory error thrown */
    }

    if(currencyCount > 0) {
         jint out[currencyCount];
         if (error.code == ABC_CC_Ok) {
             int i = 0;
             for (i = 0; i < currencyCount; ++i) {
                 out[i] = currencies[i].num;
             }
         }
        // move from the temp structure to the java structure
        (*env)->SetIntArrayRegion(env, result, 0, currencyCount, out);
        return result;
    }
    return NULL;
}

/*
 * Return code from currency at index
 */
JNIEXPORT jstring JNICALL
Java_co_airbitz_internal_Jni_getCurrencyCode(
        JNIEnv *env, jobject obj, jint currencyNum)
{
    tABC_Error error;
    int currencyCount;
    tABC_Currency *currencies;
    ABC_GetCurrencies(&currencies, &currencyCount, &error);
    if (error.code == ABC_CC_Ok) {
        int i = 0;
        for (i = 0; i < currencyCount; ++i) {
            if (currencyNum == currencies[i].num) {
                const char *buf = currencies[i].szCode;
                jstring jresult = (*env)->NewStringUTF(env, buf);
                return jresult;
            }
        }
    }
    char *buf = "";
    jstring jresult = (*env)->NewStringUTF(env, buf);
    return jresult;
}

/*
 * Return description from currency at index
 */
JNIEXPORT jstring JNICALL
Java_co_airbitz_internal_Jni_getCurrencyDescription(
        JNIEnv *env, jobject obj, jint currencyNum)
{
    tABC_Error error;
    int currencyCount;
    tABC_Currency *currencies;
    ABC_GetCurrencies(&currencies, &currencyCount, &error);
    if (error.code == ABC_CC_Ok) {
        int i = 0;
        for (i = 0; i < currencyCount; ++i) {
            if (currencyNum == currencies[i].num) {
                const char *buf = currencies[i].szDescription;
                jstring jresult = (*env)->NewStringUTF(env, buf);
                return jresult;
            }
        }
    }
    char *buf = "";
    jstring jresult = (*env)->NewStringUTF(env, buf);
    return jresult;
}

/*
 * Return 64 bit long from ptr
 */
JNIEXPORT jlong JNICALL
Java_co_airbitz_internal_Jni_get64BitLongAtPtr(
        JNIEnv *env, jobject obj, jlong ptr)
{
    char *base = *(char **) &ptr;
    int i=0;
    long long result = 0;
    for (i=0; i<8; i++) {
        long long value = base[i] & 0xff;
        result |= (value << (i*8));
    }
    return (jlong) result;
}

/*
 * Set 64 bit long at ptr
 */
JNIEXPORT void JNICALL
Java_co_airbitz_internal_Jni_set64BitLongAtPtr(
        JNIEnv *jenv, jclass jcls, jlong obj, jlong value)
{
    unsigned char *base = *(unsigned char **) &obj;
    int i = 0;
    for (i = 0; i < 8; i++) {
        base[i] = (unsigned char) ((value >> (i*8)) & 0xff);
    }
}

/*
 * Return 64 bit long from pointer
 */
JNIEXPORT jlong JNICALL
Java_co_airbitz_internal_Jni_getLongAtPtr(jlong *obj)
{
    return *obj;
}

/*
 * Proper conversion without SWIG problems
*/
JNIEXPORT jlong JNICALL
Java_co_airbitz_internal_Jni_ParseAmount(
        JNIEnv *jenv, jclass jcls, jstring jarg1, jint decimalplaces)
{
    tABC_CC result;
    (void)jenv;
    (void)jcls;

    char *instring = (char *) 0 ;
    instring = 0;
    if (jarg1) {
        instring = (char *)(*jenv)->GetStringUTFChars(jenv, jarg1, 0);
        if (!instring)
            return 0;
    }

    int64_t arg2 = 0; //*(int64_t **)&outp;
    unsigned int arg3 = (unsigned int)decimalplaces;
    result = (tABC_CC)ABC_ParseAmount(instring,&arg2,arg3);
    return arg2;
}

/*
 * Proper conversion without SWIG problems
*/
JNIEXPORT jint JNICALL
Java_co_airbitz_internal_Jni_FormatAmount(
        JNIEnv *jenv, jclass jcls, jlong satoshi, jlong ppchar,
        jlong decimalplaces, jboolean addSign, jlong perror) {
    jint jresult = 0 ;
    int64_t arg1 = satoshi;
    char **arg2 = (char **) 0 ;
    unsigned int arg3 ;
    unsigned int arg4 ;
    tABC_Error *argError = (tABC_Error *) 0 ;
    tABC_CC result;

    (void)jenv;
    (void)jcls;

    arg2 = *(char ***)&ppchar;
    arg3 = (unsigned int)decimalplaces;
    arg4 = addSign==false? 0 : 1;
    argError = *(tABC_Error **)&perror;
    result = (tABC_CC)ABC_FormatAmount(arg1, arg2, arg3, arg4, argError);
    jresult = (jint)result;
    return jresult;
}

/*
 * Proper conversion to currency without SWIG problems
*/
JNIEXPORT jint JNICALL
Java_co_airbitz_internal_Jni_satoshiToCurrency(
        JNIEnv *jenv, jobject obj,
        jstring jarg1, jstring jarg2, jlong satoshi,
        jlong currencyp, jint currencyNumber, jlong error)
{
    tABC_CC result;
    double *arg4;
    arg4 = *(double **)&currencyp;
    int number = (int) currencyNumber;
    int64_t sat = satoshi;
    tABC_Error *argError = (tABC_Error *) 0 ;
    argError = *(tABC_Error **)&error;

    char *username = (char *) 0 ;
    char *password = (char *) 0 ;

    username = 0;
    if (jarg1) {
        username = (char *)(*jenv)->GetStringUTFChars(jenv, jarg1, 0);
        if (!username)
            return 0;
    }
    password = 0;
    if (jarg2) {
        password = (char *)(*jenv)->GetStringUTFChars(jenv, jarg2, 0);
        if (!password)
            return 0;
    }
    result = ABC_SatoshiToCurrency(
                username, password, sat,
                arg4, currencyNumber, argError);
    return result;
}

