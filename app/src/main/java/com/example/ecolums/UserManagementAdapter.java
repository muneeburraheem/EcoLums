package com.example.ecolums;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for the admin Users tab. Each row shows a user with 3 action buttons.
 */
public class UserManagementAdapter extends RecyclerView.Adapter<UserManagementAdapter.VH> {

	public interface ActionListener {
		void onAction(User user);
	}

	private final List<User> users;
	private final ActionListener onReset, onDelete, onExport;

	public UserManagementAdapter(
			List<User> users,
			ActionListener onReset,
			ActionListener onDelete,
			ActionListener onExport
	) {
		this.users = users;
		this.onReset = onReset;
		this.onDelete = onDelete;
		this.onExport = onExport;
	}

	@NonNull
	@Override
	public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View v = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.item_admin_user, parent, false);
		return new VH(v);
	}

	@Override
	public void onBindViewHolder(@NonNull VH h, int pos) {
		User u = users.get(pos);

		String email = u.getEmail() != null ? u.getEmail() : "unknown";
		h.tvEmail.setText(email);
		h.tvRole.setText(u.getRole() != null ? u.getRole() : "STUDENT");
		h.tvPoints.setText(String.format(Locale.getDefault(), "%.0f pts", u.getTotalPoints()));

		// Avatar initial from first letter of email
		h.tvInitial.setText(email.substring(0, 1).toUpperCase(Locale.getDefault()));

		h.btnReset.setOnClickListener(v -> {if (onReset != null) onReset.onAction(u);});
		h.btnDelete.setOnClickListener(v -> {if (onDelete != null) onDelete.onAction(u);});
		h.btnExport.setOnClickListener(v -> {if (onExport != null) onExport.onAction(u);});
	}

	@Override
	public int getItemCount() {
		return users.size();
	}

	static class VH extends RecyclerView.ViewHolder {
		TextView tvInitial, tvEmail, tvRole, tvPoints;
		MaterialButton btnReset, btnDelete, btnExport;

		VH(View v) {
			super(v);
			tvInitial = v.findViewById(R.id.tv_user_initial);
			tvEmail = v.findViewById(R.id.tv_user_email);
			tvRole = v.findViewById(R.id.tv_user_role);
			tvPoints = v.findViewById(R.id.tv_user_points_badge);
			btnReset = v.findViewById(R.id.btn_reset_pwd);
			btnDelete = v.findViewById(R.id.btn_delete_user_data);
			btnExport = v.findViewById(R.id.btn_export_user_data);
		}
	}
}
