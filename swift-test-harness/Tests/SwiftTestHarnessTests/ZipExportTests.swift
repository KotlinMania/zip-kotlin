import Testing
import Zip

@Suite("Zip Swift Export Tests")
struct ZipExportTests {
    @Test("Zip constants and functions work from Swift")
    func swiftModuleLoads() {
        #expect(DEFAULT_VERSION >= 0)
        let ch = cp437ToChar(input: 65)
        #expect(ch == 65)
    }
}
