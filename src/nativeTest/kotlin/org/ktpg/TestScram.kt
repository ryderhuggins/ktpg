package org.ktpg

import io.ktor.utils.io.core.*
import kotlin.experimental.ExperimentalNativeApi
import kotlin.test.Test

class TestScram {
    
    @OptIn(ExperimentalStdlibApi::class)
    @Test
    @ExperimentalNativeApi
    fun testClientProof() {
        // 58ce8b4e96e3046432ed144c194564ecea80f3e7e845954cd6234b6b8c432c65
        // 58ce8b4e96e3046432ed144c194564ecea80f3e7e845954cd6234b6b8c432c65
        
        val clientFinalMessage = getScramClientFinalMessage(
            password="password123",
            r="gtuyBFXdIT/rATFuMKTGxlR2NaVo6F5zu1zf9zzE1pPLcQxp",
            s="oQG+bFlJT6uKVTGKVBW4ug=",
            i=4096,
            clientFirstMessageBare = "n=,r=gtuyBFXdIT/rATFuMKTGxlR2",
            serverFirstMessage = "r=gtuyBFXdIT/rATFuMKTGxlR2NaVo6F5zu1zf9zzE1pPLcQxp,s=oQG+bFlJT6uKVTGKVBW4ug==,i=4096"
        )
        
        assert(clientFinalMessage.clientProofB64 == "jPOEh6u95KHdK9cc+Bmq96FqvjkhjF9lhSgVvHrV4oM=")
        // POEh6u95KHdK9cc+Bmq96FqvjkhjF9lhSgVvHrV4oM=
    }
}