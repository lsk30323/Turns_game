package androidx.lifecycle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 검증 전용 스텁 — 실제 androidx.lifecycle.ViewModel 의 최소 표면.
 * Google Maven 이 차단된 환경에서 GameViewModel 의 코틀린 정확성을 컴파일/실행 검증하기 위함.
 * 실제 androidx 와 동일하게 viewModelScope 는 Dispatchers.Main.immediate 기반 SupervisorJob 스코프.
 */
open class ViewModel {
    @PublishedApi
    internal val internalScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    protected open fun onCleared() {}
}

val ViewModel.viewModelScope: CoroutineScope get() = internalScope
