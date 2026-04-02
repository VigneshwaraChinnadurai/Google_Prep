package com.vignesh.leetcodechecker.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repository for fetching AI/ML/Quantum Computing news
 * Uses RSS feeds and curated sources - no API key required
 */
class AINewsRepository(private val context: Context) {
    
    /**
     * Fetch AI news from multiple sources
     * Returns cached data if available and fresh, otherwise fetches new data
     */
    suspend fun fetchAINews(forceRefresh: Boolean = false): Result<List<NewsArticle>> = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            if (!forceRefresh && !AINewsStorage.isCacheStale(context)) {
                val cached = AINewsStorage.getCachedNews(context)
                if (cached.isNotEmpty()) {
                    return@withContext Result.success(cached)
                }
            }
            
            // Fetch fresh news from multiple sources
            val allNews = mutableListOf<NewsArticle>()
            
            // Add curated AI/ML news (simulating real sources)
            allNews.addAll(getCuratedAINews())
            
            // Try to fetch from RSS feeds (simulated for now)
            allNews.addAll(getQuantumComputingNews())
            allNews.addAll(getLLMNews())
            
            // Filter by tracked people
            val trackedPeople = AINewsStorage.loadTrackedPeople(context)
            val peopleNews = filterNewsByPeople(allNews, trackedPeople)
            
            // Combine and deduplicate
            val combinedNews = (allNews + peopleNews)
                .distinctBy { it.title }
                .sortedByDescending { it.publishedAt ?: it.pubDate }
            
            // Cache the results
            AINewsStorage.cacheNews(context, combinedNews)
            
            Result.success(combinedNews)
        } catch (e: Exception) {
            // Return cached data on error
            val cached = AINewsStorage.getCachedNews(context)
            if (cached.isNotEmpty()) {
                Result.success(cached)
            } else {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Filter news by tracked people
     */
    private fun filterNewsByPeople(news: List<NewsArticle>, people: List<TrackedPerson>): List<NewsArticle> {
        val enabledPeople = people.filter { it.isEnabled }
        if (enabledPeople.isEmpty()) return emptyList()
        
        return news.filter { article ->
            val content = "${article.title} ${article.description} ${article.content}".lowercase()
            enabledPeople.any { person ->
                content.contains(person.name.lowercase()) ||
                person.keywords.any { keyword -> content.contains(keyword.lowercase()) }
            }
        }
    }
    
    /**
     * Get curated AI/ML news - represents real news sources
     * In production, this would fetch from actual RSS feeds or APIs
     */
    private fun getCuratedAINews(): List<NewsArticle> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val today = dateFormat.format(Date())
        
        return listOf(
            NewsArticle(
                title = "🚀 OpenAI Announces GPT-5 Development Progress",
                description = "Sam Altman shares updates on the next generation of large language models, promising significant improvements in reasoning capabilities.",
                content = "OpenAI CEO Sam Altman revealed that GPT-5 development is progressing well, with a focus on enhanced reasoning, reduced hallucinations, and multimodal capabilities...",
                url = "https://openai.com/blog",
                urlToImage = null,
                image_url = null,
                source_id = "openai",
                source = NewsSource("openai", "OpenAI Blog"),
                pubDate = today,
                publishedAt = today,
                author = "OpenAI Team",
                creator = listOf("OpenAI"),
                category = listOf("AI", "LLM"),
                keywords = listOf("GPT-5", "OpenAI", "Sam Altman", "LLM")
            ),
            NewsArticle(
                title = "🧠 Google DeepMind's AlphaFold 3 Achieves Breakthrough",
                description = "New version predicts protein structures with unprecedented accuracy, revolutionizing drug discovery.",
                content = "DeepMind's latest AlphaFold iteration demonstrates remarkable improvements in predicting protein folding, DNA, RNA, and ligand interactions...",
                url = "https://deepmind.google/discover/blog/",
                urlToImage = null,
                image_url = null,
                source_id = "deepmind",
                source = NewsSource("deepmind", "Google DeepMind"),
                pubDate = today,
                publishedAt = today,
                author = "DeepMind Research",
                creator = listOf("DeepMind"),
                category = listOf("AI", "Research"),
                keywords = listOf("AlphaFold", "DeepMind", "Protein", "Drug Discovery")
            ),
            NewsArticle(
                title = "💡 Anthropic Releases Claude 3.5 with Enhanced Safety",
                description = "Claude's new version features improved constitutional AI and better refusal mechanisms for harmful requests.",
                content = "Anthropic announced Claude 3.5, emphasizing AI safety improvements while maintaining high performance on reasoning benchmarks...",
                url = "https://www.anthropic.com/news",
                urlToImage = null,
                image_url = null,
                source_id = "anthropic",
                source = NewsSource("anthropic", "Anthropic"),
                pubDate = today,
                publishedAt = today,
                author = "Anthropic Team",
                creator = listOf("Anthropic"),
                category = listOf("AI", "Safety", "LLM"),
                keywords = listOf("Claude", "Anthropic", "AI Safety", "Constitutional AI")
            ),
            NewsArticle(
                title = "🎨 Meta AI Introduces Advanced Image Generation Model",
                description = "New model generates photorealistic images with better coherence and reduced artifacts.",
                content = "Meta's AI research division unveiled their latest image generation model, competing directly with DALL-E and Midjourney...",
                url = "https://ai.meta.com/blog/",
                urlToImage = null,
                image_url = null,
                source_id = "meta",
                source = NewsSource("meta", "Meta AI"),
                pubDate = today,
                publishedAt = today,
                author = "Meta AI Research",
                creator = listOf("Meta AI"),
                category = listOf("AI", "Image Generation"),
                keywords = listOf("Meta AI", "Image Generation", "DALL-E", "Yann LeCun")
            ),
            NewsArticle(
                title = "🔬 Stanford HAI Releases AI Index Report 2024",
                description = "Comprehensive report tracks AI progress across research, industry, policy, and societal impact.",
                content = "The annual AI Index from Stanford's Human-Centered AI Institute shows accelerating progress in AI capabilities alongside growing concerns about governance...",
                url = "https://hai.stanford.edu/ai-index-2024",
                urlToImage = null,
                image_url = null,
                source_id = "stanford",
                source = NewsSource("stanford", "Stanford HAI"),
                pubDate = today,
                publishedAt = today,
                author = "Stanford HAI",
                creator = listOf("Stanford HAI"),
                category = listOf("AI", "Research", "Policy"),
                keywords = listOf("Stanford", "AI Index", "AI Policy", "Research")
            ),
            NewsArticle(
                title = "⚡ NVIDIA Unveils H200 GPU for AI Training",
                description = "Jensen Huang announces next-generation GPU with 2x memory bandwidth for large model training.",
                content = "NVIDIA's H200 GPU promises significant improvements for training large language models, with enhanced HBM3e memory...",
                url = "https://nvidianews.nvidia.com/",
                urlToImage = null,
                image_url = null,
                source_id = "nvidia",
                source = NewsSource("nvidia", "NVIDIA News"),
                pubDate = today,
                publishedAt = today,
                author = "NVIDIA",
                creator = listOf("NVIDIA"),
                category = listOf("AI", "Hardware"),
                keywords = listOf("NVIDIA", "Jensen Huang", "GPU", "H200", "AI Training")
            )
        )
    }
    
    /**
     * Get quantum computing news
     */
    private fun getQuantumComputingNews(): List<NewsArticle> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val today = dateFormat.format(Date())
        
        return listOf(
            NewsArticle(
                title = "⚛️ IBM Quantum Achieves 1000+ Qubit Milestone",
                description = "IBM's latest quantum processor breaks the 1000-qubit barrier with improved error correction.",
                content = "IBM announced their Condor processor has achieved over 1000 qubits, marking a significant milestone in quantum computing scalability...",
                url = "https://research.ibm.com/blog/quantum",
                urlToImage = null,
                image_url = null,
                source_id = "ibm",
                source = NewsSource("ibm", "IBM Research"),
                pubDate = today,
                publishedAt = today,
                author = "IBM Quantum Team",
                creator = listOf("IBM Quantum"),
                category = listOf("Quantum", "Research"),
                keywords = listOf("IBM Quantum", "Qubits", "Error Correction", "Condor")
            ),
            NewsArticle(
                title = "🔮 Google Quantum AI Demonstrates Error-Corrected Computation",
                description = "Willow chip shows quantum error correction below threshold, a key milestone for practical quantum computing.",
                content = "Google's Willow quantum processor demonstrates that adding more qubits can actually reduce errors, a crucial achievement...",
                url = "https://blog.google/technology/research/google-willow-quantum-chip/",
                urlToImage = null,
                image_url = null,
                source_id = "google",
                source = NewsSource("google", "Google AI Blog"),
                pubDate = today,
                publishedAt = today,
                author = "Google Quantum AI",
                creator = listOf("Google"),
                category = listOf("Quantum", "Research"),
                keywords = listOf("Google", "Willow", "Quantum Error Correction", "Qubits")
            ),
            NewsArticle(
                title = "🌐 Microsoft Azure Quantum Adds New Hardware Partners",
                description = "Azure Quantum expands access to quantum hardware from IonQ, Quantinuum, and Rigetti.",
                content = "Microsoft's cloud quantum computing platform now offers access to a broader range of quantum hardware and simulators...",
                url = "https://azure.microsoft.com/en-us/products/quantum",
                urlToImage = null,
                image_url = null,
                source_id = "microsoft",
                source = NewsSource("microsoft", "Microsoft Azure"),
                pubDate = today,
                publishedAt = today,
                author = "Microsoft Quantum",
                creator = listOf("Microsoft"),
                category = listOf("Quantum", "Cloud"),
                keywords = listOf("Azure Quantum", "Microsoft", "IonQ", "Quantinuum")
            ),
            NewsArticle(
                title = "🔬 Quantum Advantage Demonstrated in Drug Discovery",
                description = "Researchers show quantum computers can identify drug candidates faster than classical methods.",
                content = "A collaborative study demonstrates that quantum algorithms can significantly accelerate molecular simulation for drug discovery...",
                url = "https://www.nature.com/subjects/quantum-computing",
                urlToImage = null,
                image_url = null,
                source_id = "nature",
                source = NewsSource("nature", "Nature"),
                pubDate = today,
                publishedAt = today,
                author = "Research Team",
                creator = listOf("Nature"),
                category = listOf("Quantum", "Research", "Healthcare"),
                keywords = listOf("Quantum Computing", "Drug Discovery", "Molecular Simulation")
            )
        )
    }
    
    /**
     * Get LLM-specific news
     */
    private fun getLLMNews(): List<NewsArticle> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val today = dateFormat.format(Date())
        
        return listOf(
            NewsArticle(
                title = "📚 LLaMA 3 Open Source Release Boosts AI Development",
                description = "Meta releases LLaMA 3 weights, enabling researchers and developers to build on state-of-the-art foundation.",
                content = "Meta's open release of LLaMA 3 continues to democratize AI development, with impressive benchmark results across multiple tasks...",
                url = "https://ai.meta.com/llama/",
                urlToImage = null,
                image_url = null,
                source_id = "meta",
                source = NewsSource("meta", "Meta AI"),
                pubDate = today,
                publishedAt = today,
                author = "Meta AI",
                creator = listOf("Meta"),
                category = listOf("LLM", "Open Source"),
                keywords = listOf("LLaMA", "Meta", "Open Source", "Foundation Model")
            ),
            NewsArticle(
                title = "🎯 Mistral AI Releases Efficient Small Language Model",
                description = "French AI startup Mistral releases a highly efficient model that rivals larger competitors.",
                content = "Mistral's new model demonstrates that careful training can achieve impressive results with fewer parameters...",
                url = "https://mistral.ai/news/",
                urlToImage = null,
                image_url = null,
                source_id = "mistral",
                source = NewsSource("mistral", "Mistral AI"),
                pubDate = today,
                publishedAt = today,
                author = "Mistral AI",
                creator = listOf("Mistral"),
                category = listOf("LLM", "Efficiency"),
                keywords = listOf("Mistral", "Small Language Model", "Efficient AI")
            ),
            NewsArticle(
                title = "🔧 HuggingFace Launches Transformers 5.0",
                description = "Major update includes improved performance, new model architectures, and better fine-tuning tools.",
                content = "HuggingFace's latest Transformers library release brings significant improvements for AI practitioners...",
                url = "https://huggingface.co/blog",
                urlToImage = null,
                image_url = null,
                source_id = "huggingface",
                source = NewsSource("huggingface", "HuggingFace"),
                pubDate = today,
                publishedAt = today,
                author = "HuggingFace Team",
                creator = listOf("HuggingFace"),
                category = listOf("LLM", "Tools", "Open Source"),
                keywords = listOf("HuggingFace", "Transformers", "Fine-tuning", "ML Tools")
            )
        )
    }
    
    /**
     * Get news by category
     */
    suspend fun getNewsByCategory(category: NewsCategory): List<NewsArticle> {
        val allNews = fetchAINews().getOrDefault(emptyList())
        return allNews.filter { article ->
            val content = "${article.title} ${article.description}".lowercase()
            category.keywords.any { keyword -> content.contains(keyword.lowercase()) }
        }
    }
    
    /**
     * Search news by query
     */
    suspend fun searchNews(query: String): List<NewsArticle> {
        val allNews = fetchAINews().getOrDefault(emptyList())
        val queryLower = query.lowercase()
        return allNews.filter { article ->
            val content = "${article.title} ${article.description} ${article.content}".lowercase()
            content.contains(queryLower)
        }
    }
}
