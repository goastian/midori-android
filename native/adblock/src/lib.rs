use adblock::{
    Engine,
    lists::{FilterSet, ParseOptions},
    request::Request,
    resources::Resource,
};
use jni::{
    JNIEnv,
    objects::{JByteArray, JClass, JString},
    sys::{jbyteArray, jlong, jstring},
};
use serde_json::{Value, json};
use std::{
    collections::HashMap,
    panic::{AssertUnwindSafe, catch_unwind},
    sync::{
        Arc, OnceLock, RwLock,
        atomic::{AtomicI64, Ordering},
    },
};

struct NativeEngine {
    network: Engine,
    documents: Engine,
}

impl NativeEngine {
    fn document_exception(&self, url: &str) -> bool {
        Request::new(url, url, "document", "GET")
            .ok()
            .is_some_and(|request| {
                self.documents
                    .check_network_request_subset(&request, false, true)
                    .exception
                    .is_some()
            })
    }

    fn serialize(&self) -> Vec<u8> {
        let network = self.network.serialize();
        let mut bytes = (network.len() as u64).to_le_bytes().to_vec();
        bytes.extend(network);
        bytes.extend(self.documents.serialize());
        bytes
    }
}

static ENGINES: OnceLock<RwLock<HashMap<i64, Arc<NativeEngine>>>> = OnceLock::new();
static NEXT_ID: AtomicI64 = AtomicI64::new(1);

fn engines() -> &'static RwLock<HashMap<i64, Arc<NativeEngine>>> {
    ENGINES.get_or_init(Default::default)
}

fn insert(engine: NativeEngine) -> i64 {
    let id = NEXT_ID.fetch_add(1, Ordering::Relaxed);
    engines().write().unwrap().insert(id, Arc::new(engine));
    id
}

fn get(id: i64) -> Result<Arc<NativeEngine>, String> {
    engines()
        .read()
        .map_err(|e| e.to_string())?
        .get(&id)
        .cloned()
        .ok_or_else(|| "Closed adblock engine".into())
}

fn compile(lists: &str, resources: &str) -> Result<NativeEngine, String> {
    let lists: Vec<String> = serde_json::from_str(lists).map_err(|e| e.to_string())?;
    let mut filters = FilterSet::new(false);
    let mut documents = FilterSet::new(false);
    for text in lists {
        let exceptions = text
            .lines()
            .filter(|line| {
                line.starts_with("@@")
                    && line.rsplit_once('$').is_some_and(|(_, options)| {
                        options.split(',').any(|option| option == "document")
                    })
            })
            .collect::<Vec<_>>()
            .join("\n");
        documents.add_filter_list(exceptions, ParseOptions::default());
        filters.add_filter_list(text, ParseOptions::default());
    }
    let mut engine = Engine::new_with_filter_set(filters);
    engine.use_resources(
        serde_json::from_str::<Vec<Resource>>(resources).map_err(|e| e.to_string())?,
    );
    Ok(NativeEngine {
        network: engine,
        documents: Engine::new_with_filter_set(documents),
    })
}

fn query(engine: &NativeEngine, input: &str) -> Result<Value, String> {
    let input: Value = serde_json::from_str(input).map_err(|e| e.to_string())?;
    let field = |key| input[key].as_str().unwrap_or("");
    let request = match Request::new(
        field("url"),
        field("source"),
        field("type"),
        field("method"),
    ) {
        Ok(request) => request,
        Err(_) => return Ok(json!({"cancel": false})),
    };
    if engine.document_exception(field("document")) || engine.document_exception(field("source")) {
        return Ok(json!({"cancel": false}));
    }
    let result = engine.network.check_network_request(&request);
    let mut answer = if result.should_block() {
        match result.redirect {
            Some(url) => json!({"cancel": false, "redirectUrl": url, "blocked": true}),
            None => json!({"cancel": true, "blocked": true}),
        }
    } else {
        match result.rewritten_url {
            Some(url) => json!({"cancel": false, "redirectUrl": url}),
            None => json!({"cancel": false}),
        }
    };
    if let Some(csp) = engine.network.get_csp_directives(&request) {
        answer["csp"] = json!(csp);
    }
    Ok(answer)
}

fn guarded<T: Default>(env: &mut JNIEnv, run: impl FnOnce(&mut JNIEnv) -> Result<T, String>) -> T {
    match catch_unwind(AssertUnwindSafe(|| run(env))) {
        Ok(Ok(value)) => value,
        outcome => {
            let message = match outcome {
                Ok(Err(e)) => e,
                _ => "Native adblock failure".into(),
            };
            let _ = env.throw_new("java/lang/IllegalStateException", message);
            T::default()
        }
    }
}

fn string(env: &mut JNIEnv, value: &JString) -> Result<String, String> {
    env.get_string(value)
        .map(Into::into)
        .map_err(|e| e.to_string())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_midorinext_android_adblock_RustAdblock_create(
    mut env: JNIEnv,
    _: JClass,
    lists: JString,
    resources: JString,
) -> jlong {
    guarded(&mut env, |env| {
        Ok(insert(compile(
            &string(env, &lists)?,
            &string(env, &resources)?,
        )?))
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_midorinext_android_adblock_RustAdblock_release(
    _: JNIEnv,
    _: JClass,
    id: jlong,
) {
    if let Ok(mut engines) = engines().write() {
        engines.remove(&id);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_midorinext_android_adblock_RustAdblock_query(
    mut env: JNIEnv,
    _: JClass,
    id: jlong,
    input: JString,
) -> jstring {
    guarded(&mut env, |env| {
        let engine = get(id)?;
        let response = query(&engine, &string(env, &input)?)?.to_string();
        env.new_string(response)
            .map(|s| s.into_raw())
            .map_err(|e| e.to_string())
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_midorinext_android_adblock_RustAdblock_cosmetics(
    mut env: JNIEnv,
    _: JClass,
    id: jlong,
    input: JString,
) -> jstring {
    guarded(&mut env, |env| {
        let engine = get(id)?;
        let input: Value =
            serde_json::from_str(&string(env, &input)?).map_err(|e| e.to_string())?;
        let url = input["url"].as_str().unwrap_or("");
        let mut response = engine.network.url_cosmetic_resources(url);
        let strings = |key: &str| -> Vec<&str> {
            input[key]
                .as_array()
                .map(|a| a.iter().filter_map(Value::as_str).collect())
                .unwrap_or_default()
        };
        if !response.generichide {
            response
                .hide_selectors
                .extend(engine.network.hidden_class_id_selectors(
                    strings("classes"),
                    strings("ids"),
                    &response.exceptions,
                ));
        }
        let mut response = serde_json::to_value(&response).map_err(|e| e.to_string())?;
        response["disabled"] = json!(engine.document_exception(url));
        let response = response.to_string();
        env.new_string(response)
            .map(|s| s.into_raw())
            .map_err(|e| e.to_string())
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_midorinext_android_adblock_RustAdblock_serialize(
    mut env: JNIEnv,
    _: JClass,
    id: jlong,
) -> jbyteArray {
    guarded(&mut env, |env| {
        env.byte_array_from_slice(&get(id)?.serialize())
            .map(|a| a.into_raw())
            .map_err(|e| e.to_string())
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_midorinext_android_adblock_RustAdblock_restore(
    mut env: JNIEnv,
    _: JClass,
    data: JByteArray,
    resources: JString,
) -> jlong {
    guarded(&mut env, |env| {
        let bytes = env.convert_byte_array(data).map_err(|e| e.to_string())?;
        let prefix: [u8; 8] = bytes
            .get(..8)
            .ok_or("Truncated filter cache")?
            .try_into()
            .map_err(|_| "Invalid cache")?;
        let length = usize::try_from(u64::from_le_bytes(prefix))
            .map_err(|_| "Invalid filter cache length")?;
        let split = length
            .checked_add(8)
            .filter(|split| *split <= bytes.len())
            .ok_or("Invalid filter cache length")?;
        let mut network = Engine::default();
        network
            .deserialize(&bytes[8..split])
            .map_err(|e| e.to_string())?;
        network.use_resources(
            serde_json::from_str::<Vec<Resource>>(&string(env, &resources)?)
                .map_err(|e| e.to_string())?,
        );
        let mut documents = Engine::default();
        documents
            .deserialize(&bytes[split..])
            .map_err(|e| e.to_string())?;
        Ok(insert(NativeEngine { network, documents }))
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_midorinext_android_adblock_RustAdblock_countRules(
    mut env: JNIEnv,
    _: JClass,
    text: JString,
) -> jlong {
    guarded(&mut env, |env| {
        let text = string(env, &text)?;
        Ok(text
            .lines()
            .filter(|line| {
                adblock::lists::parse_filter(line, false, ParseOptions::default()).is_ok()
            })
            .count() as i64)
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn check(lists: &[&str], url: &str, source: &str, kind: &str) -> Value {
        let engine = compile(&json!(lists).to_string(), "[]").unwrap();
        query(
            &engine,
            &json!({"url":url,"source":source,"type":kind,"method":"GET"}).to_string(),
        )
        .unwrap()
    }

    #[test]
    fn regex_and_cross_list_badfilter() {
        let lists = [
            r"/ads[0-9]+\.js/$script",
            "||disabled.example^$image",
            "||disabled.example^$badfilter,image",
        ];
        assert_eq!(
            check(
                &lists,
                "https://cdn.example/ads42.js",
                "https://news.example/",
                "script"
            )["cancel"],
            true
        );
        assert_eq!(
            check(
                &lists,
                "https://disabled.example/banner.png",
                "https://news.example/",
                "image"
            )["cancel"],
            false
        );
    }

    #[test]
    fn important_blocks_override_exceptions_and_removeparam_keeps_content() {
        assert_eq!(
            check(
                &["||ads.example^$important", "@@||ads.example^"],
                "https://ads.example/a.js",
                "https://news.example/",
                "script"
            )["cancel"],
            true
        );
        let result = check(
            &["||news.example^$removeparam=utm_source"],
            "https://news.example/article?id=42&utm_source=ads",
            "https://news.example/",
            "xmlhttprequest",
        );
        assert_eq!(result["redirectUrl"], "https://news.example/article?id=42");
    }

    #[test]
    fn private_suffixes_and_subframes_use_request_initiator() {
        let lists = ["||cdn.blogspot.com^$third-party,script"];
        assert_eq!(
            check(
                &lists,
                "https://cdn.blogspot.com/a.js",
                "https://user.blogspot.com/",
                "script"
            )["cancel"],
            true
        );
        assert_eq!(
            check(
                &lists,
                "https://cdn.blogspot.com/a.js",
                "https://cdn.blogspot.com/",
                "script"
            )["cancel"],
            false
        );
    }
}
