import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CategoryService, CategoryRequest } from '../../core/services/category.service';
import { Category } from '../../core/models/models';

const PRESET_ICONS = ['🍔','🚗','🛍️','🎬','💊','💡','✈️','📚','🛒','📺','🏠','🛡️','💰','🏷️','🎮','☕','🏋️','🐾','🎵','📱'];
const PRESET_COLORS = ['#667eea','#f59e0b','#10b981','#ef4444','#3b82f6','#8b5cf6','#ec4899','#14b8a6','#f97316','#6366f1','#84cc16','#06b6d4'];

@Component({
  selector: 'app-categories',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './categories.component.html',
  styleUrls: ['./categories.component.scss']
})
export class CategoriesComponent implements OnInit {
  private categoryService = inject(CategoryService);

  categories: (Category & { custom?: boolean })[] = [];
  loading = false;
  error = '';

  showForm = false;
  editingId: number | null = null;
  form: CategoryRequest = { name: '', icon: '🏷️', color: '#667eea' };
  saving = false;

  presetIcons = PRESET_ICONS;
  presetColors = PRESET_COLORS;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.categoryService.getCategories().subscribe({
      next: cats => { this.categories = cats as any; this.loading = false; },
      error: () => { this.error = 'Failed to load categories'; this.loading = false; }
    });
  }

  get globalCategories() { return this.categories.filter(c => !c['custom']); }
  get customCategories() { return this.categories.filter(c => c['custom']); }

  openCreate(): void {
    this.editingId = null;
    this.form = { name: '', icon: '🏷️', color: '#667eea' };
    this.showForm = true;
    this.error = '';
  }

  openEdit(cat: Category & { custom?: boolean }): void {
    this.editingId = cat.id;
    this.form = { name: cat.name, icon: cat.icon ?? '🏷️', color: cat.color ?? '#667eea' };
    this.showForm = true;
    this.error = '';
  }

  cancelForm(): void {
    this.showForm = false;
    this.editingId = null;
    this.error = '';
  }

  save(): void {
    if (!this.form.name.trim()) return;
    this.saving = true;
    this.error = '';
    const call = this.editingId
      ? this.categoryService.updateCategory(this.editingId, this.form)
      : this.categoryService.createCategory(this.form);

    call.subscribe({
      next: () => { this.saving = false; this.showForm = false; this.load(); },
      error: (err) => {
        this.saving = false;
        this.error = err?.error?.message ?? (this.editingId ? 'Failed to update' : 'Failed to create — name may already exist');
      }
    });
  }

  delete(cat: Category): void {
    if (!confirm(`Delete category "${cat.name}"? Transactions using it will become uncategorized.`)) return;
    this.categoryService.deleteCategory(cat.id).subscribe({
      next: () => this.load(),
      error: () => { this.error = 'Failed to delete category'; }
    });
  }
}
