package com.example.data.model

import android.content.Context
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.util.NoticeSecurityManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(
    tableName = "resident_notices",
    indices = [
        Index(value = ["block", "unitNumber"]),
        Index(value = ["status"]),
        Index(value = ["scheduledDate"])
    ]
)
data class ResidentNotice(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val block: String, // "A" ou "B"
    val unitNumber: String, // "001" até "144"
    val residentName: String, // Nome do morador responsável
    val noticeType: String, // "VISITA_PROGRAMADA", "PRESTADOR_SERVICO", "ENTREGA", "AUTORIZACAO_ENTRADA", "MANUTENCAO", "OUTRO"
    val visitorName: String = "", // Nome do visitante ou prestador
    val visitorDocument: String = "", // Documento de identificação protegido (criptografado em repouso)
    val vehiclePlate: String = "", // Placa do veículo (opcional)
    val vehicleModel: String = "", // Modelo/cor do veículo (opcional)
    val scheduledDate: String, // "AAAA-MM-DD"
    val scheduledTime: String = "", // "HH:mm"
    val additionalNotes: String = "", // Observações adicionais
    val registeredByGuardName: String = "", // Nome do porteiro que cadastrou
    val status: String = "PENDENTE", // "PENDENTE", "VISUALIZADO", "CONCLUIDO", "CANCELADO"
    val createdAtTimestamp: Long = System.currentTimeMillis(),
    val lastUpdatedByGuardName: String = "",
    val updatedAtTimestamp: Long = System.currentTimeMillis(),
    val auditTrail: String = "" // Histórico de auditoria com autor e horário das alterações
) {
    val unitFullIdentifier: String
        get() = "$block-$unitNumber"

    val noticeTypeDisplayName: String
        get() = when (noticeType) {
            "VISITA_PROGRAMADA" -> "Visita programada"
            "PRESTADOR_SERVICO" -> "Prestador de serviço"
            "ENTREGA" -> "Entrega"
            "AUTORIZACAO_ENTRADA" -> "Autorização de entrada"
            "MANUTENCAO" -> "Manutenção"
            else -> "Outro"
        }

    val noticeTypeEmoji: String
        get() = when (noticeType) {
            "VISITA_PROGRAMADA" -> "🚶"
            "PRESTADOR_SERVICO" -> "🔧"
            "ENTREGA" -> "📦"
            "AUTORIZACAO_ENTRADA" -> "🔑"
            "MANUTENCAO" -> "🛠️"
            else -> "📌"
        }

    val statusDisplayName: String
        get() = when (status) {
            "PENDENTE" -> "Pendente"
            "VISUALIZADO" -> "Visualizado"
            "CONCLUIDO" -> "Concluído"
            "CANCELADO" -> "Cancelado"
            else -> status
        }

    val officialHeaderIdentifier: String
        get() = "${noticeTypeDisplayName.uppercase()} — BLOCO $block / UNIDADE $unitNumber"

    fun formattedScheduledDate(): String {
        val parts = scheduledDate.split("-")
        return if (parts.size == 3) {
            "${parts[2]}/${parts[1]}/${parts[0]}"
        } else {
            scheduledDate
        }
    }

    fun maskedDocument(context: Context? = null): String {
        return NoticeSecurityManager.maskDocument(visitorDocument, context)
    }

    fun decryptedDocument(context: Context? = null): String {
        return NoticeSecurityManager.decryptDocument(visitorDocument, context)
    }

    fun appendAuditEntry(action: String, guardName: String, timestamp: Long = System.currentTimeMillis()): String {
        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))
        val actor = guardName.trim().ifBlank { "Porteiro" }
        val entry = "[${fmt.format(Date(timestamp))}] $actor: $action"
        return if (auditTrail.isBlank()) entry else "$auditTrail\n$entry"
    }
}
