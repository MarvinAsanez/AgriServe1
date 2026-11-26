package com.group7.agriserve.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Generates an official A4 PDF Supply Request & Release Slip for approved or distributed DA aid requests.
 */
object ReleaseSlipPdfHelper {

    data class ReleaseSlipData(
        val slipNumber: String,
        val referenceId: String,
        val farmerName: String,
        val rsbsaNumber: String,
        val farmAddress: String,
        val itemName: String,
        val category: String,
        val quantity: String,
        val unit: String,
        val farmSize: String,
        val requestedAt: String,
        val approvedAt: String,
        val approvedBy: String,
        val distributedAt: String,
        val status: String,
        val claimingVenue: String,
        val purpose: String
    )

    fun generateAndOpenSlip(context: Context, data: ReleaseSlipData) {
        val pdf = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas

            val headerPaint = Paint().apply {
                color = Color.rgb(27, 94, 32)
                textSize = 14f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val subHeaderPaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 11f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val badgePaint = Paint().apply {
                color = Color.rgb(46, 125, 50)
                textSize = 11.5f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val labelPaint = Paint().apply {
                color = Color.rgb(71, 85, 105)
                textSize = 10f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val valuePaint = Paint().apply {
                color = Color.BLACK
                textSize = 10.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val borderPaint = Paint().apply {
                color = Color.rgb(180, 190, 200)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            val noteBgPaint = Paint().apply {
                color = Color.rgb(241, 248, 233)
                style = Paint.Style.FILL
            }
            val linePaint = Paint().apply {
                color = Color.BLACK
                strokeWidth = 0.9f
            }
            val smallPaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 9f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }

            // Letterhead
            canvas.drawText("REPUBLIC OF THE PHILIPPINES — DEPARTMENT OF AGRICULTURE", 297.5f, 56f, headerPaint)
            canvas.drawText("Municipal Agriculture Office (MAO) • Official Supply Request & Release Slip", 297.5f, 76f, subHeaderPaint)
            canvas.drawText("SLIP NO: ${data.slipNumber}  •  STATUS: ${data.status.uppercase()}", 297.5f, 98f, badgePaint)

            val left = 48f
            val mid = 215f
            val right = 547f
            var y = 122f
            val rowH = 30f

            val rows = listOf(
                "Reference ID" to data.referenceId,
                "Slip Number" to data.slipNumber,
                "Farmer Beneficiary" to data.farmerName,
                "RSBSA Reference #" to data.rsbsaNumber.ifBlank { "Enrolled" },
                "Farm Location" to data.farmAddress.ifBlank { "Registered Farm Address" },
                "Approved Supply / Input" to "${data.itemName} (${data.category})",
                "Approved Quantity" to "${data.quantity} ${data.unit} (Farm Size: ${data.farmSize.ifBlank { "N/A" }} ha)",
                "Requested Date" to data.requestedAt,
                "Approved Date / Officer" to "${data.approvedAt} • ${data.approvedBy}",
                "Release / Claim Status" to data.distributedAt,
                "Claiming Venue" to data.claimingVenue,
                "Purpose / Remarks" to data.purpose.ifBlank { "Agricultural support" }
            )

            rows.forEach { (label, rawVal) ->
                val valText = rawVal.take(58)
                canvas.drawRect(left, y, mid, y + rowH, borderPaint)
                canvas.drawRect(mid, y, right, y + rowH, borderPaint)
                canvas.drawText(label, left + 8f, y + 19f, labelPaint)
                canvas.drawText(valText, mid + 8f, y + 19f, valuePaint)
                y += rowH
            }

            // Claiming instructions box
            y += 18f
            canvas.drawRect(left, y, right, y + 54f, noteBgPaint)
            canvas.drawRect(left, y, right, y + 54f, borderPaint)
            canvas.drawText("CLAIMING INSTRUCTION:", left + 10f, y + 18f, labelPaint)
            canvas.drawText("Present this Official Release Slip together with a valid Government ID", left + 10f, y + 34f, smallPaint)
            canvas.drawText("or RSBSA Registration Stub at ${data.claimingVenue}.", left + 10f, y + 46f, smallPaint)

            // Signature lines
            val sigY = y + 120f
            canvas.drawLine(60f, sigY, 240f, sigY, linePaint)
            canvas.drawText("Farmer Beneficiary Signature", 82f, sigY + 15f, smallPaint)

            canvas.drawLine(345f, sigY, 535f, sigY, linePaint)
            canvas.drawText("DA / MAO Approving Officer", 372f, sigY + 15f, smallPaint)

            pdf.finishPage(page)

            val safeSlip = data.slipNumber.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val fileName = "AgriServe_ReleaseSlip_${safeSlip}.pdf"
            val uri = savePdfToStorage(context, pdf, fileName)
            if (uri != null) {
                Toast.makeText(context, "Saved $fileName to Downloads/AgriServe", Toast.LENGTH_LONG).show()
                openPdfUri(context, uri)
            } else {
                Toast.makeText(context, "Unable to save release slip PDF.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Release Slip PDF error: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            pdf.close()
        }
    }

    private fun savePdfToStorage(context: Context, pdf: PdfDocument, fileName: String): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AgriServe")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out -> pdf.writeTo(out) }
                    return uri
                }
            }
            val dir = File(context.cacheDir, "release_slips")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { out -> pdf.writeTo(out) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Exception) {
            null
        }
    }

    private fun openPdfUri(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open DA Release Slip"))
        } catch (_: Exception) {
            Toast.makeText(context, "Slip saved to Downloads/AgriServe. Open with any PDF viewer.", Toast.LENGTH_LONG).show()
        }
    }
}
