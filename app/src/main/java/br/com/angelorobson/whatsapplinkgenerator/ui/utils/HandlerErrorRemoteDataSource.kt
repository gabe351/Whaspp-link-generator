package br.com.angelorobson.whatsapplinkgenerator.ui.utils

import br.com.angelorobson.whatsapplinkgenerator.ui.utils.handlerstatuscode.HandlerErrorStatusCode
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object HandlerErrorRemoteDataSource {

    private const val noInternetConnectionErrorCodeEnum = 600
    private const val unProcessableEntityStatusCode = 422
    private const val socketTimoutStatusCode = 1000

    fun validateStatusCode(ex: Throwable): String {
        var errorId = "0"
        errorId = when (ex) {
            is UnknownHostException -> {
                handlerException(noInternetConnectionErrorCodeEnum)
            }
            is SocketTimeoutException -> {
                handlerException(socketTimoutStatusCode)
            }
            is ConnectException -> {
                handlerException(noInternetConnectionErrorCodeEnum)
            }
            is IOException -> {
                handlerException(noInternetConnectionErrorCodeEnum)
            }
            else -> handlerException(0)
        }

        return errorId
    }

    private fun handlerException(statusCode: Int): String {
        val messageFromStringResource = HandlerErrorStatusCode.fromInt(statusCode)
        return messageFromStringResource?.getMessageFromResourceString().toString()
    }
}