use jni::{
    objects::{JByteArray, JClass},
    sys::{jbyteArray, jlong},
    JNIEnv,
};
use midori_sync_core::mobile::MobileCrypto;
use std::{
    collections::HashMap,
    panic::{catch_unwind, AssertUnwindSafe},
    sync::{
        atomic::{AtomicI64, Ordering},
        Mutex, OnceLock,
    },
};
use zeroize::Zeroizing;

static RINGS: OnceLock<Mutex<HashMap<i64, MobileCrypto>>> = OnceLock::new();
static NEXT: AtomicI64 = AtomicI64::new(1);

#[no_mangle]
pub extern "system" fn Java_org_midorinext_android_sync_NativeSync_create(
    mut env: JNIEnv,
    _: JClass,
) -> jlong {
    let id = NEXT.fetch_add(1, Ordering::Relaxed);
    let Ok(mut rings) = RINGS.get_or_init(Default::default).lock() else {
        return 0;
    };
    if rings.len() >= 16 || id <= 0 {
        let _ = env.throw_new("java/lang/IllegalStateException", "sync_crypto_capacity");
        return 0;
    }
    rings.insert(id, MobileCrypto::default());
    id
}

#[no_mangle]
pub extern "system" fn Java_org_midorinext_android_sync_NativeSync_request(
    mut env: JNIEnv,
    _: JClass,
    id: jlong,
    input: JByteArray,
) -> jbyteArray {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if env
            .get_array_length(&input)
            .map_err(|_| "invalid_request")?
            > 1_048_576
        {
            return Err("request_too_large");
        }
        let bytes = Zeroizing::new(
            env.convert_byte_array(input)
                .map_err(|_| "invalid_request")?,
        );
        let mut rings = RINGS
            .get_or_init(Default::default)
            .lock()
            .map_err(|_| "crypto_failed")?;
        let output = rings.get_mut(&id).ok_or("keys_closed")?.request(&bytes)?;
        env.byte_array_from_slice(&output)
            .map(|v| v.into_raw())
            .map_err(|_| "crypto_failed")
    }))
    .unwrap_or(Err("crypto_failed"));
    match result {
        Ok(array) => array,
        Err(error) => {
            let _ = env.throw_new("java/lang/IllegalStateException", error);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_org_midorinext_android_sync_NativeSync_close(
    _: JNIEnv,
    _: JClass,
    id: jlong,
) {
    if let Ok(mut rings) = RINGS.get_or_init(Default::default).lock() {
        rings.remove(&id);
    }
}
