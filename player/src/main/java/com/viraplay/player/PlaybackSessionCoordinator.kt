package com.viraplay.player

/**
 * Evita duas conexões simultâneas ao mesmo servidor IPTV.
 *
 * A tela de prévia fica por baixo do overlay do player principal. Em contas
 * com 1 conexão permitida, a prévia podia manter o stream aberto por alguns
 * instantes e o servidor recusava o filme/canal aberto em seguida.
 */
object PlaybackSessionCoordinator {
    @Volatile
    private var stopPreviewAction: (() -> Unit)? = null

    @Synchronized
    fun registerPreviewStop(action: () -> Unit) {
        stopPreviewAction = action
    }

    @Synchronized
    fun unregisterPreviewStop(action: () -> Unit) {
        if (stopPreviewAction === action) {
            stopPreviewAction = null
        }
    }

    @Synchronized
    fun stopPreview() {
        val action = stopPreviewAction
        stopPreviewAction = null
        runCatching { action?.invoke() }
    }
}
