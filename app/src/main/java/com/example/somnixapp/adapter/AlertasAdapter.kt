package com.example.somnixapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.somnixapp.databinding.ItemAlertaBinding
import com.example.somnixapp.models.response.AlertaResponse

class AlertasAdapter(
    private var alertas: List<AlertaResponse>,
    private val onLeerClick: (AlertaResponse) -> Unit
) : RecyclerView.Adapter<AlertasAdapter.AlertaViewHolder>() {

    inner class AlertaViewHolder(val binding: ItemAlertaBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertaViewHolder {
        val binding = ItemAlertaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AlertaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlertaViewHolder, position: Int) {
        val alerta = alertas[position]

        holder.binding.txtNivelAlerta.text = alerta.nivel.uppercase()
        holder.binding.txtMensajeAlerta.text = alerta.mensaje
        holder.binding.txtDetalleAlerta.text = "${alerta.tipo} • ${alerta.fechaRegistro}"

        if (alerta.atendida) {
            holder.binding.btnLeerAlerta.text = "Leída"
            holder.binding.btnLeerAlerta.isEnabled = false
            holder.binding.btnLeerAlerta.alpha = 0.5f
        } else {
            holder.binding.btnLeerAlerta.text = "Marcar como leída"
            holder.binding.btnLeerAlerta.isEnabled = true
            holder.binding.btnLeerAlerta.alpha = 1f
            holder.binding.btnLeerAlerta.setOnClickListener {
                onLeerClick(alerta)
            }
        }
    }

    override fun getItemCount(): Int = alertas.size

    fun actualizarLista(nuevaLista: List<AlertaResponse>) {
        alertas = nuevaLista
        notifyDataSetChanged()
    }
}