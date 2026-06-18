package com.lsk.cardgame.domain.net

import kotlinx.serialization.json.Json

/** 클라이언트/서버 공용 JSON 설정. sealed 메시지의 다형 직렬화 판별자는 "type". */
val ProtocolJson: Json = Json {
    classDiscriminator = "type"
    encodeDefaults = true
    ignoreUnknownKeys = true
}
