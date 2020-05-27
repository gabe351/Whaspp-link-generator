package br.com.angelorobson.whatsapplinkgenerator.ui.linkgenerator

import android.widget.Toast
import br.com.angelorobson.whatsapplinkgenerator.model.domains.History
import br.com.angelorobson.whatsapplinkgenerator.model.repositories.CountryRepository
import br.com.angelorobson.whatsapplinkgenerator.model.repositories.HistoryRepository
import br.com.angelorobson.whatsapplinkgenerator.ui.MobiusVM
import br.com.angelorobson.whatsapplinkgenerator.ui.share.copyToClipBoard
import br.com.angelorobson.whatsapplinkgenerator.ui.share.sendMessageToWhatsApp
import br.com.angelorobson.whatsapplinkgenerator.ui.share.showToast
import br.com.angelorobson.whatsapplinkgenerator.ui.utils.ActivityService
import br.com.angelorobson.whatsapplinkgenerator.ui.utils.HandlerErrorRemoteDataSource.validateStatusCode
import br.com.angelorobson.whatsapplinkgenerator.ui.utils.Navigator
import com.spotify.mobius.Next
import com.spotify.mobius.Next.*
import com.spotify.mobius.Update
import com.spotify.mobius.rx2.RxMobius
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.threeten.bp.LocalDateTime
import javax.inject.Inject


fun linkGeneratorUpdate(
    model: LinkGeneratorModel,
    event: LinkGeneratorEvent
): Next<LinkGeneratorModel, LinkGeneratorEffect> {
    return when (event) {
        is Initial -> dispatch(setOf(ObserveCountriesEffect))
        is CountriesLoadedEvent -> next(
            model.copy(
                linkGeneratorResult = LinkGeneratorResult.CountriesLoaded(
                    countries = event.countries,
                    isLoading = false
                )
            )
        )
        is CountriesExceptionEvent -> next(
            model.copy(
                linkGeneratorResult = LinkGeneratorResult.Error(
                    errorMessage = event.errorMessage,
                    isLoading = false
                )
            )
        )
        FormInvalidEvent -> noChange()
        is ButtonSendClickedEvent ->
            if (event.isFormValid) {
                dispatch<LinkGeneratorModel, LinkGeneratorEffect>(
                    setOf(
                        SaveHistoryEffect(
                            history = History(
                                createdAt = getNow(),
                                country = event.country,
                                message = event.message,
                                phoneNumber = event.phoneNumber
                            )
                        )
                    )
                )
            } else {
                noChange()
            }
        is ButtonCopyClickedEvent ->
            if (event.isFormValid) {
                dispatch<LinkGeneratorModel, LinkGeneratorEffect>(
                    setOf(
                        CopyToClipBoardEffect(
                            countryCode = event.countryCode,
                            phoneNumber = event.phoneNumber,
                            message = event.message
                        )
                    )
                )
            } else {
                noChange()
            }
        is SendMessageToWhatsAppEvent -> dispatch(setOf(SendMessageToWhatsAppEffect(event.history)))
    }
}

class LinkGeneratorViewModel @Inject constructor(
    repository: CountryRepository,
    historyRepository: HistoryRepository,
    navigator: Navigator,
    activityService: ActivityService
) : MobiusVM<LinkGeneratorModel, LinkGeneratorEvent, LinkGeneratorEffect>(
    "LinkGeneratorViewModel",
    Update(::linkGeneratorUpdate),
    LinkGeneratorModel(),
    RxMobius.subtypeEffectHandler<LinkGeneratorEffect, LinkGeneratorEvent>()
        .addTransformer(ObserveCountriesEffect::class.java) { upstream ->
            upstream.switchMap {
                repository.getAllFromApi()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .map {
                        CountriesLoadedEvent(it)
                    }
                    .doOnError {
                        val errorMessage = validateStatusCode(it)
                        showToast(errorMessage, activityService.activity, Toast.LENGTH_LONG)
                        CountriesExceptionEvent(errorMessage)
                    }
            }
        }
        .addTransformer(SaveHistoryEffect::class.java) { upstream ->
            upstream.switchMap { effect ->
                historyRepository.saveHistory(effect.history)
                    .subscribeOn(Schedulers.newThread())
                    .toSingleDefault(SendMessageToWhatsAppEvent(effect.history))
                    .toObservable()
                    .doOnError {
                        CountriesExceptionEvent(it.localizedMessage)
                    }

            }
        }
        .addConsumer(CopyToClipBoardEffect::class.java) { effect ->
            copyToClipBoard(
                activityService.activity,
                effect.countryCode,
                effect.phoneNumber,
                effect.message
            )
        }.addConsumer(SendMessageToWhatsAppEffect::class.java) { effect ->
            sendMessageToWhatsApp(
                activityService.activity,
                effect.history
            )
        }
        .build()

)

fun getNow(): String {
    return LocalDateTime.now().toString()
}
