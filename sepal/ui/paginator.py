import math

from flask import get_template_attribute


class Paginator:
    @staticmethod
    def create(current_page, page_size, num_items, total_count):
        p = Paginator()
        p.total_count = total_count
        p.start_index = (current_page - 1) * page_size + 1 if num_items > 0 else 0
        if num_items == 0:
            p.end_index = 0
        elif num_items > page_size:
            p.end_index = p.start_index + page_size - 1
        else:
            p.end_index = p.start_index + num_items - 1
        p.num_pages = math.ceil(total_count / page_size)
        p.previous_page = current_page - 1 if current_page > 1 else None
        p.next_page = current_page + 1 if current_page < p.num_pages else None
        return p

    def __html__(self):
        return self.__call__()

    def __call__(self, **kwargs):
        render = get_template_attribute("form/paginator.html", "render_paginator")
        return render(
            start_index=self.start_index,
            end_index=self.end_index,
            previous_page=self.previous_page,
            next_page=self.next_page,
            total_count=self.total_count,
        )
