package org.ktpg

import kotlin.experimental.ExperimentalNativeApi
import kotlin.test.Test

class TestScram {
    
    @Test
    @ExperimentalNativeApi
    fun testClientProof() {
        val clientFinalMessage = getScramClientFinalMessage(
            password="password123",
            r="gtuyBFXdIT/rATFuMKTGxlR2NaVo6F5zu1zf9zzE1pPLcQxp",
            s="oQG+bFlJT6uKVTGKVBW4ug=",
            i=4096,
            clientFirstMessageBare = "n=,r=gtuyBFXdIT/rATFuMKTGxlR2",
            serverFirstMessage = "r=gtuyBFXdIT/rATFuMKTGxlR2NaVo6F5zu1zf9zzE1pPLcQxp,s=oQG+bFlJT6uKVTGKVBW4ug==,i=4096"
        )
        
        assert(clientFinalMessage.clientProofB64 == "jPOEh6u95KHdK9cc+Bmq96FqvjkhjF9lhSgVvHrV4oM=")
    }
}