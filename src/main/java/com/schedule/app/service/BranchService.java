package com.schedule.app.service;

import com.schedule.app.dto.request.BranchRequest;
import com.schedule.app.dto.response.BranchResponse;
import com.schedule.app.entity.Branch;
import com.schedule.app.exception.ResourceNotFoundException;
import com.schedule.app.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;

    public List<BranchResponse> getAll() {
        return branchRepository.findAll().stream().map(this::toResponse).toList();
    }

    public BranchResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Transactional
    public BranchResponse create(BranchRequest.Create request) {
        Branch branch = Branch.builder()
                .name(request.name())
                .address(request.address())
                .build();
        return toResponse(branchRepository.save(branch));
    }

    @Transactional
    public BranchResponse update(Long id, BranchRequest.Update request) {
        Branch branch = findById(id);
        branch.setName(request.name());
        branch.setAddress(request.address());
        return toResponse(branchRepository.save(branch));
    }

    @Transactional
    public void delete(Long id) {
        findById(id);
        branchRepository.deleteById(id);
    }

    public Branch findById(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with id: " + id));
    }

    private BranchResponse toResponse(Branch branch) {
        return new BranchResponse(
                branch.getId(),
                branch.getName(),
                branch.getAddress(),
                branch.getCreatedAt(),
                branch.getUpdatedAt()
        );
    }
}
