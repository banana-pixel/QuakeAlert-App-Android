package id.my.bananapixel.quakealert.di

import android.content.Context
import id.my.bananapixel.quakealert.db.Database
import id.my.bananapixel.quakealert.db.Repository
import id.my.bananapixel.quakealert.ui.DetailViewModel
import id.my.bananapixel.quakealert.ui.QuakeViewModel
import id.my.bananapixel.quakealert.ui.SubscriptionsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    single {
        androidContext().getSharedPreferences(Repository.SHARED_PREFS_ID, Context.MODE_PRIVATE)
    }
    single {
        val db = Database.build(androidContext())
        Database.hiltInstance = db
        db
    }
    single {
        val repo = Repository(get(), get())
        Repository.hiltInstance = repo
        repo
    }
}
