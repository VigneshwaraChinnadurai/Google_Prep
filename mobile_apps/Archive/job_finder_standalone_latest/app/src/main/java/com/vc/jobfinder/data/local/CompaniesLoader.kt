package com.vc.jobfinder.data.local

import android.content.Context
import com.charleskorn.kaml.Yaml
import com.vc.jobfinder.domain.CompaniesProvider
import com.vc.jobfinder.domain.Company
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class CompanyDto(
    val name: String,
    val ats: String,
    val slug: String? = null,
    val location_filter: String? = null,
)

@Singleton
class CompaniesLoader @Inject constructor(
    @ApplicationContext private val ctx: Context,
) : CompaniesProvider {
    override val companies: List<Company> by lazy {
        try {
            val yaml = ctx.assets.open("companies.yaml").bufferedReader().use { it.readText() }
            Yaml.default.decodeFromString(ListSerializer(CompanyDto.serializer()), yaml)
                .filter { it.slug != null && it.ats in supportedAts }
                .map { Company(
                    name = it.name,
                    ats = it.ats,
                    slug = it.slug!!,
                    locationFilter = it.location_filter,
                )}
        } catch (t: Throwable) {
            android.util.Log.e("CompaniesLoader", "Failed to load companies.yaml", t)
            emptyList()
        }
    }

    companion object {
        private val supportedAts = setOf("greenhouse", "lever")
    }
}
