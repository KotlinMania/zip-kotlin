import Testing
import Zip

@Suite("Zip Swift Export Tests")
struct ZipExportTests {
    @Test("Zip swift module imported cleanly")
    func swiftModuleLoads() {
        #expect(Bool(true), "Zip swift module imported cleanly")
    }
}
