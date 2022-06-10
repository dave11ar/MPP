import org.jetbrains.kotlinx.lincheck.LoggingLevel.INFO
import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.annotations.Param
import org.jetbrains.kotlinx.lincheck.check
import org.jetbrains.kotlinx.lincheck.paramgen.IntGen
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.StressOptions
import org.jetbrains.kotlinx.lincheck.verifier.VerifierState
import org.junit.Test

@Param(name = "element", gen = IntGen::class, conf = "1:5")
class SkipListSetTest {
    private val set = SkipListSet(infinityLeft = Int.MIN_VALUE, infinityRight = Int.MAX_VALUE)

    @Operation
    fun add(@Param(name = "element") element: Int): Boolean = set.add(element)

    @Operation
    fun remove(@Param(name = "element") element: Int): Boolean = set.remove(element)

    @Operation
    fun contains(@Param(name = "element") element: Int): Boolean = set.contains(element)

    @Test
    fun stressTest() = StressOptions()
        .sequentialSpecification(SkipListSetSequential::class.java)
        .check(this::class.java)

    @Test
    fun modelCheckingTest() = ModelCheckingOptions()
        .sequentialSpecification(SkipListSetSequential::class.java)
        .check(this::class.java)
}

class SkipListSetSequential : VerifierState() {
    private val set = HashSet<Int>()

    fun add(@Param(name = "element") element: Int): Boolean = set.add(element)
    fun remove(@Param(name = "element") element: Int): Boolean = set.remove(element)
    fun contains(@Param(name = "element") element: Int): Boolean = set.contains(element)

    override fun extractState() = set
}