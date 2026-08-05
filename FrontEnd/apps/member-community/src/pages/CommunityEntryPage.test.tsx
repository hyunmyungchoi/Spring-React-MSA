import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchCommunityPosts } from '../api/communityApi'
import CommunityEntryPage from './CommunityEntryPage'

vi.mock('../api/communityApi', () => ({
  createCommunityPost: vi.fn(),
  deleteCommunityPost: vi.fn(),
  fetchCommunityPosts: vi.fn(),
  updateCommunityPost: vi.fn(),
}))

const mockedFetchCommunityPosts = vi.mocked(fetchCommunityPosts)

describe('CommunityEntryPage', () => {
  beforeEach(() => {
    mockedFetchCommunityPosts.mockResolvedValue([
      {
        id: 1,
        author: 'member-a',
        title: 'Owned post',
        content: 'Can be changed',
        createdAt: '2026-08-02T00:00:00Z',
        updatedAt: '2026-08-02T00:00:00Z',
        ownedByCurrentUser: true,
      },
      {
        id: 2,
        author: 'member-b',
        title: 'Foreign post',
        content: 'Read only',
        createdAt: '2026-08-02T00:00:00Z',
        updatedAt: '2026-08-02T00:00:00Z',
        ownedByCurrentUser: false,
      },
    ])
  })

  it('shows mutation controls only for a post owned by the current user', async () => {
    render(<CommunityEntryPage />)

    expect(await screen.findByText('Owned post')).toBeInTheDocument()
    expect(screen.getByText('Foreign post')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Edit' })).toHaveLength(1)
    expect(screen.getAllByRole('button', { name: 'Delete' })).toHaveLength(1)
  })
})

