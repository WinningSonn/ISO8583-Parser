import org.example.Iso8583ParserService
import javax.swing.*
import javax.swing.table.DefaultTableModel
import java.awt.BorderLayout
import java.awt.Dimension

fun main() {
    val parserService = Iso8583ParserService()

    // Main frame
    val frame = JFrame("ISO 8583 Message Parser")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.layout = BorderLayout()
    frame.size = Dimension(800, 600)

    // Input panel
    val inputPanel = JPanel(BorderLayout())
    val isoInputField = JTextArea(5, 60)
    isoInputField.lineWrap = true
    isoInputField.wrapStyleWord = true
    val scrollPane = JScrollPane(isoInputField)
    inputPanel.add(JLabel("Enter ISO 8583 Message:"), BorderLayout.NORTH)
    inputPanel.add(scrollPane, BorderLayout.CENTER)

    // Table to display parsed fields
    val tableModel = DefaultTableModel(arrayOf("Field Number", "Label", "Type", "Length","Value"), 0)
    val resultTable = JTable(tableModel)
    val tableScrollPane = JScrollPane(resultTable)

    // Parse button
    val parseButton = JButton("Parse ISO Message")

    // Button action
    parseButton.addActionListener {
        try {
            val isoMessage = isoInputField.text.trim()
            if (isoMessage.isNotEmpty()) {
                // Clear previous results from the table
                tableModel.setRowCount(0)

                // Parse the ISO message using the parser service
                val parsedMessage = parserService.parseIsoMessage(isoMessage)

                // Add fields to the table
                for (field in parsedMessage.fields) {
                    tableModel.addRow(arrayOf(field.fieldNumber, field.label ?: "N/A", field.type, field.length,field.value))
                }

                // Popup a success message
                JOptionPane.showMessageDialog(frame, "ISO Message parsed!", "Success", JOptionPane.INFORMATION_MESSAGE)
            } else {
                // Show an error if the input is empty
                JOptionPane.showMessageDialog(frame, "Please enter a valid ISO 8583 message.", "Error", JOptionPane.ERROR_MESSAGE)
            }
        } catch (e: Exception) {
            // Handle parsing errors
            JOptionPane.showMessageDialog(frame, "Error parsing ISO message: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
        }
    }

    // Add components to the frame
    frame.add(inputPanel, BorderLayout.NORTH)
    frame.add(tableScrollPane, BorderLayout.CENTER)
    frame.add(parseButton, BorderLayout.SOUTH)

    // Set the frame to be visible
    frame.isVisible = true
}